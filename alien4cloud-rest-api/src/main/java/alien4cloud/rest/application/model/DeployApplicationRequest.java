package alien4cloud.rest.application.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DeployApplicationRequest {
    private String applicationId;
    private String applicationEnvironmentId;
    private Object secretProviderCredentials;
    private String secretProviderPluginName;
}
