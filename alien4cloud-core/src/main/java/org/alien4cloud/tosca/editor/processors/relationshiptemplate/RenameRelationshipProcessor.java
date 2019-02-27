package org.alien4cloud.tosca.editor.processors.relationshiptemplate;

import org.alien4cloud.tosca.editor.EditionContextManager;
import org.alien4cloud.tosca.editor.operations.relationshiptemplate.RenameRelationshipOperation;
import org.alien4cloud.tosca.model.Csar;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.RelationshipTemplate;
import org.alien4cloud.tosca.model.templates.Topology;
import org.springframework.stereotype.Component;

import alien4cloud.exception.AlreadyExistException;
import alien4cloud.exception.InvalidNameException;
import lombok.extern.slf4j.Slf4j;

/**
 * Rename a relationship.
 */
@Slf4j
@Component
public class RenameRelationshipProcessor extends AbstractRelationshipProcessor<RenameRelationshipOperation> {
    @Override
    protected void processRelationshipOperation(Csar csar, Topology topology, RenameRelationshipOperation operation, NodeTemplate nodeTemplate, RelationshipTemplate relationshipTemplate) {
        if (operation.getNewRelationshipName() == null || operation.getNewRelationshipName().isEmpty()) {
            throw new InvalidNameException("relationshipName", operation.getNewRelationshipName(), "Not null or empty");
        }

        String topologyId = topology.getId();

        // check that the node has not another relation with this name.
        if (nodeTemplate.getRelationships().keySet().contains(operation.getNewRelationshipName())) {
            // a relation already exist with the given name.
            throw new AlreadyExistException("A relationship with the given name " + operation.getNewRelationshipName() + " already exists in the node template "
                    + operation.getNodeName() + " of topology " + topologyId + ".");
        }

        relationshipTemplate.setName(operation.getNewRelationshipName());
        nodeTemplate.getRelationships().remove(operation.getRelationshipName());
        nodeTemplate.getRelationships().put(operation.getNewRelationshipName(), relationshipTemplate);

        log.debug("Renaming the relationship [ {} ] with [ {} ] in the node template [ {} ] of topology [ {} ] .", operation.getRelationshipName(),
                operation.getNewRelationshipName(), operation.getNodeName(), topologyId);
    }
}
