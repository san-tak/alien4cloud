package org.alien4cloud.tosca.editor.processors.policies;

import java.util.LinkedHashMap;

import javax.inject.Inject;

import org.alien4cloud.tosca.catalog.index.IToscaTypeSearchService;
import org.alien4cloud.tosca.editor.EditionContextManager;
import org.alien4cloud.tosca.editor.operations.policies.AddPolicyOperation;
import org.alien4cloud.tosca.editor.processors.IEditorOperationProcessor;
import org.alien4cloud.tosca.model.Csar;
import org.alien4cloud.tosca.model.templates.PolicyTemplate;
import org.alien4cloud.tosca.model.templates.Topology;
import org.alien4cloud.tosca.model.types.PolicyType;
import org.springframework.stereotype.Component;

import alien4cloud.topology.TopologyService;
import alien4cloud.tosca.topology.TemplateBuilder;
import alien4cloud.utils.AlienUtils;
import alien4cloud.utils.NameValidationUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * Add a new policy to the topology.
 */
@Slf4j
@Component
public class AddPolicyProcessor implements IEditorOperationProcessor<AddPolicyOperation> {
    @Inject
    private IToscaTypeSearchService toscaTypeSearchService;
    @Inject
    private TopologyService topologyService;

    @Override
    public void process(Csar csar, Topology topology, AddPolicyOperation operation) {
        NameValidationUtils.validate("policy", operation.getPolicyName());
        AlienUtils.failIfExists(topology.getPolicies(), operation.getPolicyName(), "A policy with the given name {} already exists in the topology {}.",
                operation.getPolicyName(), topology.getId());

        PolicyType policyType = toscaTypeSearchService.findByIdOrFail(PolicyType.class, operation.getPolicyTypeId());

        if (topology.getPolicies() == null) {
            topology.setPolicies(new LinkedHashMap<>());
        }

        PolicyTemplate policyTemplate = TemplateBuilder.buildPolicyTemplate(policyType);
        policyTemplate.setName(operation.getPolicyName());

        log.debug("Adding a new policy template <" + operation.getPolicyName() + "> of type <" + operation.getPolicyTypeId() + "> to the topology <"
                + topology.getId() + "> .");

        topologyService.loadType(topology, policyType);
        topology.getPolicies().put(operation.getPolicyName(), policyTemplate);
    }
}
