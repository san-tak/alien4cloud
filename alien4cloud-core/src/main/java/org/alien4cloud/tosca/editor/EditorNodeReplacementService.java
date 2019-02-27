package org.alien4cloud.tosca.editor;

import javax.inject.Inject;

import org.alien4cloud.tosca.model.templates.Topology;
import org.alien4cloud.tosca.model.types.NodeType;
import org.springframework.stereotype.Service;

import alien4cloud.topology.TopologyService;
import org.alien4cloud.tosca.utils.TopologyUtils;

/**
 * Helper service for editor context that allows to get possible replacement indexedNodeTypes for a node template.
 */
@Service
public class EditorNodeReplacementService {
    @Inject
    private TopologyService topologyService;
    @Inject
    private EditionContextManager editionContextManager;

    /**
     * Utility method to get possible replacement indexedNodeTypes for a node template
     *
     * @param topologyId The id of the topology for which to find possible nodes replacement.
     * @param nodeTemplateName The name of the node template for which to get possible nodes replacement.
     * @return An array of possible replacement for a node template.
     */
    public NodeType[] getReplacementForNode(String topologyId, String nodeTemplateName) {
        try {
            editionContextManager.init(topologyId);
            // check authorization to update a topology
            topologyService.checkEditionAuthorizations(EditionContextManager.getTopology());
            Topology topology = EditionContextManager.getTopology();

            TopologyUtils.getNodeTemplate(topologyId, nodeTemplateName, TopologyUtils.getNodeTemplates(topology));
            return topologyService.findReplacementForNode(nodeTemplateName, topology);
        } finally {
            editionContextManager.destroy();
        }
    }
}
