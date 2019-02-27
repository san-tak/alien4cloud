package org.alien4cloud.tosca.editor.processors.nodetemplate;

import javax.annotation.Resource;

import org.alien4cloud.tosca.editor.operations.nodetemplate.RenameNodeOperation;
import org.alien4cloud.tosca.editor.processors.IEditorOperationProcessor;
import org.alien4cloud.tosca.model.Csar;
import org.alien4cloud.tosca.model.templates.Topology;
import org.springframework.stereotype.Component;

import alien4cloud.paas.wf.WorkflowsBuilderService;
import alien4cloud.topology.TopologyService;
import org.alien4cloud.tosca.utils.TopologyUtils;
import alien4cloud.utils.AlienUtils;
import alien4cloud.utils.NameValidationUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * Process a {@link RenameNodeOperation}
 */
@Slf4j
@Component
public class RenameNodeProcessor implements IEditorOperationProcessor<RenameNodeOperation> {
    @Resource
    private TopologyService topologyService;
    @Resource
    private WorkflowsBuilderService workflowBuilderService;

    @Override
    public void process(Csar csar, Topology topology, RenameNodeOperation operation) {
        NameValidationUtils.validateNodeName(operation.getNewName());
        AlienUtils.failIfExists(topology.getNodeTemplates(), operation.getNewName(),
                "A node template with the given name {} already exists in the topology {}.", operation.getNodeName(), topology.getId());

        log.debug("Renaming the Node template [ {} ] with [ {} ] in the topology [ {} ] .", operation.getNodeName(), operation.getNewName(), topology.getId());
        TopologyUtils.renameNodeTemplate(topology, operation.getNodeName(), operation.getNewName());
        workflowBuilderService.renameNode(topology, csar, operation.getNodeName(), operation.getNewName());
    }
}
