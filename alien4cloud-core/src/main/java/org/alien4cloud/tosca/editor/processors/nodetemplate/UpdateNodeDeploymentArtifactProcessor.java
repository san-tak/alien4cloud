package org.alien4cloud.tosca.editor.processors.nodetemplate;

import java.util.Map;

import org.alien4cloud.tosca.editor.operations.nodetemplate.UpdateNodeDeploymentArtifactOperation;
import org.alien4cloud.tosca.editor.processors.FileProcessorHelper;
import org.alien4cloud.tosca.editor.processors.IEditorOperationProcessor;
import org.alien4cloud.tosca.model.Csar;
import org.springframework.stereotype.Component;

import alien4cloud.component.repository.ArtifactRepositoryConstants;
import alien4cloud.exception.NotFoundException;
import org.alien4cloud.tosca.model.definitions.DeploymentArtifact;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.Topology;

import org.alien4cloud.tosca.utils.TopologyUtils;

/**
 * Process an {@link UpdateNodeDeploymentArtifactOperation}.
 */
@Component
public class UpdateNodeDeploymentArtifactProcessor implements IEditorOperationProcessor<UpdateNodeDeploymentArtifactOperation> {
    @Override
    public void process(Csar csar, Topology topology, UpdateNodeDeploymentArtifactOperation operation) {
        // Get the node template's artifacts to update
        Map<String, NodeTemplate> nodeTemplates = TopologyUtils.getNodeTemplates(topology);
        NodeTemplate nodeTemplate = TopologyUtils.getNodeTemplate(topology.getId(), operation.getNodeName(), nodeTemplates);
        DeploymentArtifact artifact = nodeTemplate.getArtifacts() == null ? null : nodeTemplate.getArtifacts().get(operation.getArtifactName());
        if (artifact == null) {
            throw new NotFoundException("Artifact with key [" + operation.getArtifactName() + "] do not exist");
        }

        if (operation.getArtifactRepository() == null) {
            // this is an archive file, ensure that the file exists within the archive
            FileProcessorHelper.getFileTreeNode(operation.getArtifactReference());
            artifact.setArtifactRepository(ArtifactRepositoryConstants.ALIEN_TOPOLOGY_REPOSITORY);
            artifact.setRepositoryName(null);
            artifact.setRepositoryURL(null);
        } else {
            artifact.setArtifactRepository(operation.getArtifactRepository());
            artifact.setRepositoryName(operation.getRepositoryName());
            artifact.setRepositoryURL(operation.getRepositoryUrl());
        }
        artifact.setArtifactRef(operation.getArtifactReference());
        artifact.setArchiveName(operation.getArchiveName());
        artifact.setArchiveVersion(operation.getArchiveVersion());
    }
}