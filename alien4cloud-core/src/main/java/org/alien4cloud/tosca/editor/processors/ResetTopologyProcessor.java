package org.alien4cloud.tosca.editor.processors;

import java.io.IOException;

import javax.inject.Inject;

import org.alien4cloud.tosca.editor.EditionContextManager;
import org.alien4cloud.tosca.editor.operations.ResetTopologyOperation;
import org.alien4cloud.tosca.model.Csar;
import org.alien4cloud.tosca.model.templates.Topology;
import org.springframework.stereotype.Component;

import alien4cloud.paas.wf.WorkflowsBuilderService;
import lombok.extern.slf4j.Slf4j;

/**
 * process {@link ResetTopologyOperation}
 * This will delete everything inside the topology, leaving it as if it is just created now.
 */
@Slf4j
@Component
public class ResetTopologyProcessor implements IEditorOperationProcessor<ResetTopologyOperation> {
    @Inject
    private WorkflowsBuilderService workflowBuilderService;

    @Override
    public void process(Csar csar, Topology topology, ResetTopologyOperation operation) {
        Topology newTopology = new Topology();
        newTopology.setArchiveName(topology.getArchiveName());
        newTopology.setArchiveVersion(topology.getArchiveVersion());
        newTopology.setWorkspace(topology.getWorkspace());
        workflowBuilderService.initWorkflows(workflowBuilderService.buildTopologyContext(newTopology, csar));
        try {
            EditionContextManager.get().reset(newTopology);
        } catch (IOException e) {
            // FIXME what to do here????
            log.error("Error occurs when trying to reset the topology <" + topology.getId() + ">", e);
        }
    }
}
