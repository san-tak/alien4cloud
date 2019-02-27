package org.alien4cloud.tosca.editor.processors.workflow;

import org.alien4cloud.tosca.editor.EditionContextManager;
import org.alien4cloud.tosca.editor.operations.workflow.ConnectStepFromOperation;
import org.alien4cloud.tosca.model.Csar;
import org.alien4cloud.tosca.model.templates.Topology;
import org.alien4cloud.tosca.model.workflow.Workflow;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Process the {@link ConnectStepFromOperation} operation
 */
@Slf4j
@Component
public class ConnectStepFromProcessor extends AbstractWorkflowProcessor<ConnectStepFromOperation> {

    @Override
    protected void processWorkflowOperation(Csar csar, Topology topology, ConnectStepFromOperation operation, Workflow workflow) {
        log.debug("connecting steps [ {} ] to [ {} ] in the workflow [ {} ] from topology [ {} ]", StringUtils.join(operation.getFromStepIds(), ","),
                operation.getToStepId(), workflow.getName(), topology.getId());
        workflowBuilderService.connectStepFrom(topology, csar, workflow.getName(), operation.getToStepId(),
                operation.getFromStepIds());
    }
}
