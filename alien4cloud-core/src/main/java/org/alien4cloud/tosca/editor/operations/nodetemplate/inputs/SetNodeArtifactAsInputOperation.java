package org.alien4cloud.tosca.editor.operations.nodetemplate.inputs;

import lombok.Getter;
import lombok.Setter;
import org.alien4cloud.tosca.editor.operations.nodetemplate.AbstractNodeOperation;

/**
 * Allows to affect a get_input function to the property of a node.
 */
@Getter
@Setter
public class SetNodeArtifactAsInputOperation extends AbstractNodeOperation {
    private String inputName;
    private String artifactName;
    private boolean isNewArtifact = false;

    @Override
    public String commitMessage() {
        return "set the artifact <" + inputName + "> of node <" + getNodeName() + "> to input artifact <" + artifactName + ">";
    }
}