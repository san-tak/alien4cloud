package alien4cloud.paas.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.validator.constraints.NotBlank;

import java.util.Map;

/**
 * Object defining a request to execute an operation on a node template.
 * 
 * @author 'Igor Ngouagna'
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NodeOperationExecRequest {
    @NotBlank
    String nodeTemplateName;
    /** Instance Id of the node template on which to execute the command **/
    String instanceId;
    /** Interface where the custom command is defined **/
    @NotBlank
    String interfaceName;
    /** The name of the custom command: "operation" in the interface **/
    @NotBlank
    String operationName;
    /** Eventual parameters to the command **/
    Map<String, Object> parameters;
}
