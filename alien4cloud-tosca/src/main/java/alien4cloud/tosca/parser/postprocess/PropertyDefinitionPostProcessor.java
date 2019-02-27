package alien4cloud.tosca.parser.postprocess;

import java.util.Map;
import java.util.Set;

import org.alien4cloud.tosca.exceptions.ConstraintValueDoNotMatchPropertyTypeException;
import org.alien4cloud.tosca.exceptions.ConstraintViolationException;
import org.alien4cloud.tosca.model.definitions.PropertyConstraint;
import org.alien4cloud.tosca.model.definitions.PropertyDefinition;
import org.alien4cloud.tosca.model.types.DataType;
import org.alien4cloud.tosca.normative.types.IPropertyType;
import org.alien4cloud.tosca.normative.types.ToscaTypes;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.nodes.Node;

import com.google.common.collect.Sets;

import alien4cloud.tosca.context.ToscaContext;
import alien4cloud.tosca.model.ArchiveRoot;
import alien4cloud.tosca.parser.ParsingContextExecution;
import alien4cloud.tosca.parser.ParsingError;
import alien4cloud.tosca.parser.ParsingErrorLevel;
import alien4cloud.tosca.parser.impl.ErrorCode;
import alien4cloud.utils.services.ConstraintPropertyService;

/**
 * Performs validation of a property definition:
 * - check that constraints can apply on type and are well defined
 * - check that default value match the definition.
 */
@Component
public class PropertyDefinitionPostProcessor implements IPostProcessor<Map.Entry<String, PropertyDefinition>> {
    @Override
    public void process(Map.Entry<String, PropertyDefinition> instance) {
        PropertyDefinition propertyDefinition = instance.getValue();
        validateType(propertyDefinition);
        if (propertyDefinition.getConstraints() != null) {
            Set<String> definedConstraints = Sets.newHashSet();
            for (PropertyConstraint constraint : propertyDefinition.getConstraints()) {
                validate(propertyDefinition, constraint);
                if (!definedConstraints.add(constraint.getClass().getName())) {
                    Node node = ParsingContextExecution.getObjectToNodeMap().get(constraint);
                    ParsingContextExecution.getParsingErrors().add(new ParsingError(ParsingErrorLevel.ERROR, ErrorCode.VALIDATION_ERROR,
                            "ToscaPropertyConstraintDuplicate", node.getStartMark(), "Constraint duplicated", node.getEndMark(), "constraint"));
                }
            }
        }

        if (propertyDefinition.getDefault() != null) {
            checkDefaultValue(instance.getKey(), propertyDefinition);
        }
    }

    private void checkDefaultValue(String propertyName, PropertyDefinition propertyDefinition) {
        try {
            ConstraintPropertyService.checkPropertyConstraint(propertyName, propertyDefinition.getDefault().getValue(), propertyDefinition);
        } catch (ConstraintValueDoNotMatchPropertyTypeException | ConstraintViolationException e) {
            Node node = ParsingContextExecution.getObjectToNodeMap().get(propertyDefinition.getDefault());
            StringBuilder problem = new StringBuilder("Validation issue ");
            if (e.getConstraintInformation() != null) {
                problem.append("for " + e.getConstraintInformation().toString());
            }
            problem.append(e.getMessage());
            ParsingContextExecution.getParsingErrors().add(new ParsingError(ParsingErrorLevel.ERROR, ErrorCode.VALIDATION_ERROR, propertyName,
                    node.getStartMark(), problem.toString(), node.getEndMark(), propertyName));
        }
    }

    private void validateType(PropertyDefinition propertyDefinition) {
        String propertyType = propertyDefinition.getType();
        if (propertyType == null) {
            Node node = ParsingContextExecution.getObjectToNodeMap().get(propertyType);
            if (node == null) {
                node = ParsingContextExecution.getObjectToNodeMap().get(propertyDefinition);
            }
            ParsingContextExecution.getParsingErrors().add(new ParsingError(ErrorCode.VALIDATION_ERROR, "ToscaPropertyType", node.getStartMark(),
                    "Property type must be defined", node.getEndMark(), "type"));
        } else if (!ToscaTypes.isSimple(propertyType)) {
            if (ToscaTypes.LIST.equals(propertyType) || ToscaTypes.MAP.equals(propertyType)) {
                PropertyDefinition entrySchema = propertyDefinition.getEntrySchema();
                if (entrySchema == null) {
                    Node node = ParsingContextExecution.getObjectToNodeMap().get(propertyDefinition);
                    ParsingContextExecution.getParsingErrors().add(new ParsingError(ErrorCode.VALIDATION_ERROR, "ToscaPropertyType", node.getStartMark(),
                            "Type " + propertyType + " must define entry schema", node.getEndMark(), "type"));
                } else {
                    validateType(entrySchema);
                }
            } else {
                // It's data type
                ArchiveRoot archiveRoot = ParsingContextExecution.getRootObj();
                if (!archiveRoot.getDataTypes().containsKey(propertyType)) {
                    DataType dataType = ToscaContext.get(DataType.class, propertyType);
                    if (dataType == null) {
                        Node node = ParsingContextExecution.getObjectToNodeMap().get(propertyType);
                        ParsingContextExecution.getParsingErrors().add(new ParsingError(ErrorCode.TYPE_NOT_FOUND, "ToscaPropertyType", node.getStartMark(),
                                "Type " + propertyType + " is not found.", node.getEndMark(), "type"));
                    }
                }
            }
        }
    }

    private void validate(PropertyDefinition propertyDefinition, PropertyConstraint constraint) {
        IPropertyType<?> toscaType = ToscaTypes.fromYamlTypeName(propertyDefinition.getType());
        if (toscaType == null) {
            Node node = ParsingContextExecution.getObjectToNodeMap().get(propertyDefinition.getType());
            ParsingContextExecution.getParsingErrors().add(new ParsingError(ParsingErrorLevel.ERROR, ErrorCode.INVALID_CONSTRAINT, "Constraint parsing issue",
                    node.getStartMark(), "Limitation - Constraint cannot be used for type " + propertyDefinition.getType(), node.getEndMark(), "constraint"));
        } else {
            try {
                constraint.initialize(toscaType);
            } catch (ConstraintValueDoNotMatchPropertyTypeException e) {
                Node node = ParsingContextExecution.getObjectToNodeMap().get(constraint);
                ParsingContextExecution.getParsingErrors().add(new ParsingError(ParsingErrorLevel.ERROR, ErrorCode.VALIDATION_ERROR, "ToscaPropertyConstraint",
                        node.getStartMark(), e.getMessage(), node.getEndMark(), "constraint"));
            }
        }
    }
}
