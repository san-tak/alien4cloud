package org.alien4cloud.tosca.editor.processors.nodetemplate.outputs;

import alien4cloud.exception.NotFoundException;
import org.alien4cloud.tosca.model.Csar;
import org.alien4cloud.tosca.model.definitions.IValue;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.Topology;
import alien4cloud.utils.AlienUtils;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.alien4cloud.tosca.editor.EditionContextManager;
import org.alien4cloud.tosca.editor.operations.nodetemplate.outputs.SetNodeAttributeAsOutputOperation;
import org.alien4cloud.tosca.editor.processors.nodetemplate.AbstractNodeProcessor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Set a given attribute of a node as output for the topology.
 */
@Slf4j
@Component
public class SetNodeAttributeAsOutputProcessor extends AbstractNodeProcessor<SetNodeAttributeAsOutputOperation> {

    @Override
    protected void processNodeOperation(Csar csar, Topology topology, SetNodeAttributeAsOutputOperation operation, NodeTemplate nodeTemplate) {

        // check if the attribute exists
        Map<String, IValue> attributes = nodeTemplate.getAttributes();
        if (!AlienUtils.safe(attributes).containsKey(operation.getAttributeName())) {
            throw new NotFoundException("Attribute " + operation.getAttributeName() + "not found in node template " + operation.getNodeName() + ".");
        }

        Map<String, Set<String>> outputs = topology.getOutputAttributes();
        if (outputs == null) {
            outputs = Maps.newHashMap();
        }

        if (outputs.containsKey(operation.getNodeName())) {
            outputs.get(operation.getNodeName()).add(operation.getAttributeName());
        } else {
            outputs.put(operation.getNodeName(), Sets.newHashSet(operation.getAttributeName()));
        }

        topology.setOutputAttributes(outputs);

        log.debug("Set node [ {} ]'s attribute [ {} ] as output for the topology [ {} ].", operation.getNodeName(), operation.getAttributeName(), topology.getId());
    }

}