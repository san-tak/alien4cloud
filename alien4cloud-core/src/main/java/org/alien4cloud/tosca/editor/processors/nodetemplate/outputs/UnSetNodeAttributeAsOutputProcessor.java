package org.alien4cloud.tosca.editor.processors.nodetemplate.outputs;

import java.util.Map;
import java.util.Set;

import org.alien4cloud.tosca.editor.EditionContextManager;
import org.alien4cloud.tosca.editor.operations.nodetemplate.outputs.UnSetNodeAttributeAsOutputOperation;
import org.alien4cloud.tosca.editor.processors.nodetemplate.AbstractNodeProcessor;
import org.alien4cloud.tosca.model.Csar;
import org.springframework.stereotype.Component;

import alien4cloud.exception.NotFoundException;
import org.alien4cloud.tosca.model.definitions.IValue;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.Topology;
import alien4cloud.utils.AlienUtils;
import alien4cloud.utils.MapUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * Unset a given attribute of a node as output for the topology.
 */
@Slf4j
@Component
public class UnSetNodeAttributeAsOutputProcessor extends AbstractNodeProcessor<UnSetNodeAttributeAsOutputOperation> {

    @Override
    protected void processNodeOperation(Csar csar, Topology topology, UnSetNodeAttributeAsOutputOperation operation, NodeTemplate nodeTemplate) {

        // check if the attribute exists
        check(operation, topology, nodeTemplate);
        Map<String, Set<String>> outputs = topology.getOutputAttributes();
        removeAndClean(operation, outputs);
        topology.setOutputAttributes(outputs);

        log.debug("Unset node [ {} ]'s attribute [ {} ] as output for the topology [ {} ].", operation.getNodeName(), operation.getAttributeName(), topology.getId());
    }

    private void removeAndClean(UnSetNodeAttributeAsOutputOperation operation, Map<String, Set<String>> outputs) {
        outputs.get(operation.getNodeName()).remove(operation.getAttributeName());
        if (outputs.get(operation.getNodeName()).isEmpty()) {
            outputs.remove(operation.getNodeName());
        }
    }

    @SuppressWarnings("unchecked")
    private void check(UnSetNodeAttributeAsOutputOperation operation, Topology topology, NodeTemplate nodeTemplate) {
        Map<String, IValue> attributes = nodeTemplate.getAttributes();
        if (!AlienUtils.safe(attributes).containsKey(operation.getAttributeName())) {
            throw new NotFoundException("Attribute " + operation.getAttributeName() + "not found in node template " + operation.getNodeName() + ".");
        }

        Set<String> values = (Set<String>) MapUtil.get(topology.getOutputAttributes(), operation.getNodeName());
        if (!AlienUtils.safe(values).contains(operation.getAttributeName())) {
            throw new NotFoundException("Node " + operation.getNodeName() + " 's attribute " + operation.getAttributeName() + " not found in outputs");
        }
    }

}