package org.alien4cloud.tosca.editor.processors.substitution;

import java.util.Map;

import org.alien4cloud.tosca.editor.EditionContextManager;
import org.alien4cloud.tosca.editor.operations.substitution.RemoveCapabilitySubstitutionTypeOperation;
import org.alien4cloud.tosca.editor.processors.IEditorOperationProcessor;
import org.alien4cloud.tosca.model.Csar;
import org.springframework.stereotype.Component;

import alien4cloud.exception.NotFoundException;
import org.alien4cloud.tosca.model.templates.SubstitutionTarget;
import org.alien4cloud.tosca.model.templates.Topology;

/**
 * Delete a group from a topology.
 */
@Component
public class RemoveCapabilitySubstitutionTypeProcessor implements IEditorOperationProcessor<RemoveCapabilitySubstitutionTypeOperation> {

    @Override
    public void process(Csar csar, Topology topology, RemoveCapabilitySubstitutionTypeOperation operation) {
        if (topology.getSubstitutionMapping() == null || topology.getSubstitutionMapping().getSubstitutionType() == null) {
            throw new NotFoundException("No substitution type has been found");
        }

        Map<String, SubstitutionTarget> substitutionCapabilities = topology.getSubstitutionMapping().getCapabilities();
        if (substitutionCapabilities == null) {
            throw new NotFoundException("No substitution capabilities has been found");
        }
        SubstitutionTarget target = substitutionCapabilities.remove(operation.getSubstitutionCapabilityId());
        if (target == null) {
            throw new NotFoundException("No substitution capability has been found for key " + operation.getSubstitutionCapabilityId());
        }
    }
}