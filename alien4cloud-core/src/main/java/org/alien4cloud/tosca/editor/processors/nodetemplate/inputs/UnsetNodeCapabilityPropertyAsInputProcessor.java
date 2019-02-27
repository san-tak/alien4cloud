package org.alien4cloud.tosca.editor.processors.nodetemplate.inputs;

import static alien4cloud.paas.function.FunctionEvaluator.isGetInput;
import static alien4cloud.utils.AlienUtils.getOrFail;

import org.alien4cloud.tosca.editor.EditionContextManager;
import org.alien4cloud.tosca.editor.operations.nodetemplate.inputs.UnsetNodeCapabilityPropertyAsInputOperation;
import org.alien4cloud.tosca.editor.processors.nodetemplate.AbstractNodeProcessor;
import org.alien4cloud.tosca.model.Csar;
import org.alien4cloud.tosca.model.templates.Topology;
import org.springframework.stereotype.Component;

import alien4cloud.exception.NotFoundException;
import org.alien4cloud.tosca.model.definitions.AbstractPropertyValue;
import org.alien4cloud.tosca.model.types.CapabilityType;
import org.alien4cloud.tosca.model.definitions.PropertyDefinition;
import org.alien4cloud.tosca.model.templates.Capability;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import alien4cloud.tosca.context.ToscaContext;
import alien4cloud.utils.PropertyUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * Remove association to an input in the property of a node template's capability.
 */
@Slf4j
@Component
public class UnsetNodeCapabilityPropertyAsInputProcessor extends AbstractNodeProcessor<UnsetNodeCapabilityPropertyAsInputOperation> {
    @Override
    protected void processNodeOperation(Csar csar, Topology topology, UnsetNodeCapabilityPropertyAsInputOperation operation, NodeTemplate nodeTemplate) {
        Capability capabilityTemplate = getOrFail(nodeTemplate.getCapabilities(), operation.getCapabilityName(), "Capability {} do not exist for node {}",
                operation.getCapabilityName(), operation.getNodeName());

        // check if the node property value is a get_input
        AbstractPropertyValue currentValue = capabilityTemplate.getProperties().get(operation.getPropertyName());
        if (!isGetInput(currentValue)) {
            throw new NotFoundException("Property {} of node {} is not associated to an input.", operation.getPropertyName(), operation.getNodeName());
        }

        CapabilityType capabilityType = ToscaContext.get(CapabilityType.class, capabilityTemplate.getType());
        PropertyDefinition capabilityPropertyDefinition = getOrFail(capabilityType.getProperties(), operation.getPropertyName(),
                "Property {} do not exist for capability {} of node {}", operation.getPropertyName(), operation.getCapabilityName(), operation.getNodeName());

        AbstractPropertyValue defaultPropertyValue = PropertyUtil.getDefaultPropertyValueFromPropertyDefinition(capabilityPropertyDefinition);
        capabilityTemplate.getProperties().put(operation.getPropertyName(), defaultPropertyValue);

        log.debug("Remove association from property [ {} ] of capability template [ {} ] of node [ {} ] to an input of the topology [ {} ].",
                operation.getPropertyName(), operation.getCapabilityName(), operation.getNodeName(), topology.getId());
    }
}
