package alien4cloud.rest.runtime;

import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.Resource;
import javax.inject.Inject;
import javax.validation.Valid;

import org.alien4cloud.tosca.catalog.index.IToscaTypeSearchService;
import org.alien4cloud.tosca.exceptions.ConstraintFunctionalException;
import org.alien4cloud.tosca.exceptions.ConstraintRequiredParameterException;
import org.alien4cloud.tosca.exceptions.ConstraintValueDoNotMatchPropertyTypeException;
import org.alien4cloud.tosca.exceptions.ConstraintViolationException;
import org.alien4cloud.tosca.model.CSARDependency;
import org.alien4cloud.tosca.model.definitions.IValue;
import org.alien4cloud.tosca.model.definitions.Interface;
import org.alien4cloud.tosca.model.definitions.Operation;
import org.alien4cloud.tosca.model.definitions.PropertyDefinition;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.Topology;
import org.alien4cloud.tosca.model.types.NodeType;
import org.alien4cloud.tosca.topology.TopologyDTOBuilder;
import org.alien4cloud.tosca.utils.FunctionEvaluator;
import org.alien4cloud.tosca.utils.TopologyUtils;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import com.google.common.collect.Lists;

import alien4cloud.application.ApplicationEnvironmentService;
import alien4cloud.application.ApplicationService;
import alien4cloud.audit.annotation.Audit;
import alien4cloud.deployment.DeploymentRuntimeService;
import alien4cloud.deployment.DeploymentRuntimeStateService;
import alien4cloud.deployment.DeploymentService;
import alien4cloud.exception.NotFoundException;
import alien4cloud.model.application.Application;
import alien4cloud.model.application.ApplicationEnvironment;
import alien4cloud.model.components.IndexedModelUtils;
import alien4cloud.model.deployment.Deployment;
import alien4cloud.model.deployment.DeploymentTopology;
import alien4cloud.paas.IPaaSCallback;
import alien4cloud.paas.exception.OperationExecutionException;
import alien4cloud.paas.exception.OrchestratorDisabledException;
import alien4cloud.paas.model.OperationExecRequest;
import alien4cloud.paas.plan.TopologyTreeBuilderService;
import alien4cloud.rest.model.RestError;
import alien4cloud.rest.model.RestErrorCode;
import alien4cloud.rest.model.RestResponse;
import alien4cloud.rest.model.RestResponseBuilder;
import alien4cloud.security.AuthorizationUtil;
import alien4cloud.topology.TopologyDTO;
import alien4cloud.tosca.context.ToscaContext;
import alien4cloud.tosca.properties.constraints.ConstraintUtil.ConstraintInformation;
import alien4cloud.utils.services.ConstraintPropertyService;
import alien4cloud.utils.services.PropertyService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import lombok.extern.slf4j.Slf4j;

@RestController
@Slf4j
@RequestMapping({ "/rest/runtime", "/rest/v1/runtime", "/rest/latest/runtime" })
@Api
public class RuntimeController {
    @Resource
    private DeploymentService deploymentService;
    @Resource
    private ApplicationService applicationService;
    @Resource
    private ApplicationEnvironmentService applicationEnvironmentService;
    @Inject
    private IToscaTypeSearchService toscaTypeSearchService;
    @Inject
    private TopologyDTOBuilder topologyDTOBuilder;
    @Inject
    private TopologyTreeBuilderService topologyTreeBuilderService;
    @Inject
    private DeploymentRuntimeStateService deploymentRuntimeStateService;
    @Inject
    private DeploymentRuntimeService deploymentRuntimeService;

    @ApiOperation(value = "Trigger a custom command on a specific node template of a topology .", authorizations = {
            @Authorization("APPLICATION_MANAGER") }, notes = "Returns a response with no errors and the command response as data in success case. Application role required [ APPLICATION_MANAGER ]")
    @RequestMapping(value = "/{applicationId:.+?}/operations", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @PreAuthorize("isAuthenticated()")
    @Audit(bodyHiddenFields = { "secretProviderCredentials" })
    public DeferredResult<RestResponse<Object>> executeOperation(@PathVariable String applicationId,
            @RequestBody @Valid OperationExecRequest operationRequest) {
        final DeferredResult<RestResponse<Object>> result = new DeferredResult<>(15L * 60L * 1000L);
        Application application = applicationService.getOrFail(applicationId);
        ApplicationEnvironment environment = applicationEnvironmentService.getEnvironmentByIdOrDefault(applicationId,
                operationRequest.getApplicationEnvironmentId());
        AuthorizationUtil.checkAuthorizationForEnvironment(application, environment);

        Topology topology = deploymentRuntimeStateService.getRuntimeTopologyFromEnvironment(operationRequest.getApplicationEnvironmentId());
        // validate the operation request
        try {
            validateCommand(operationRequest, topology);
        } catch (ConstraintViolationException e) {
            result.setErrorResult(RestResponseBuilder.<Object> builder().data(e.getConstraintInformation())
                    .error(new RestError(RestErrorCode.PROPERTY_CONSTRAINT_VIOLATION_ERROR.getCode(), e.getMessage())).build());
            return result;
        } catch (ConstraintValueDoNotMatchPropertyTypeException e) {
            result.setErrorResult(RestResponseBuilder.<Object> builder().data(e.getConstraintInformation())
                    .error(new RestError(RestErrorCode.PROPERTY_TYPE_VIOLATION_ERROR.getCode(), e.getMessage())).build());
            return result;
        } catch (ConstraintRequiredParameterException e) {
            result.setErrorResult(RestResponseBuilder.<Object> builder().data(e.getConstraintInformation())
                    .error(new RestError(RestErrorCode.PROPERTY_REQUIRED_VIOLATION_ERROR.getCode(), e.getMessage())).build());
            return result;
        } catch (ConstraintFunctionalException e) {
            result.setErrorResult(RestResponseBuilder.<Object> builder().data(e.getConstraintInformation())
                    .error(new RestError(RestErrorCode.PROPERTY_UNKNOWN_VIOLATION_ERROR.getCode(), e.getMessage())).build());
            return result;
        }
        // try to trigger the execution of the operation
        try {
            deploymentRuntimeService.triggerOperationExecution(operationRequest, new IPaaSCallback<Map<String, String>>() {
                @Override
                public void onSuccess(Map<String, String> data) {
                    result.setResult(RestResponseBuilder.<Object> builder().data(data).build());
                }

                @Override
                public void onFailure(Throwable throwable) {
                    result.setErrorResult(RestResponseBuilder.<Object> builder()
                            .error(new RestError(RestErrorCode.NODE_OPERATION_EXECUTION_ERROR.getCode(), throwable.getMessage())).build());
                }
            });
        } catch (OperationExecutionException e) {
            result.setErrorResult(RestResponseBuilder.<Object> builder()
                    .error(new RestError(RestErrorCode.NODE_OPERATION_EXECUTION_ERROR.getCode(), e.getMessage())).build());
        } catch (OrchestratorDisabledException e) {
            result.setErrorResult(
                    RestResponseBuilder.<Object> builder().error(new RestError(RestErrorCode.CLOUD_DISABLED_ERROR.getCode(), e.getMessage())).build());
        }
        return result;
    }

    /**
     * Get runtime (deployed) topology of an application on a specific environment
     * 
     * @param applicationId application id for which to get the topology
     * @param applicationEnvironmentId application environment for which to get the topology through the version
     * @return {@link RestResponse}<{@link TopologyDTO}> containing the requested runtime {@link Topology} and the
     *         {@link NodeType} related to his {@link NodeTemplate}s
     */
    @ApiOperation(value = "Get runtime (deployed) topology of an application on a specific cloud.")
    @RequestMapping(value = "/{applicationId:.+?}/environment/{applicationEnvironmentId:.+?}/topology", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    public RestResponse<TopologyDTO> getDeployedTopology(
            @ApiParam(value = "Id of the application for which to get deployed topology.", required = true) @PathVariable String applicationId,
            @ApiParam(value = "Id of the environment for which to get deployed topology.", required = true) @PathVariable String applicationEnvironmentId) {

        ApplicationEnvironment environment = applicationEnvironmentService.getEnvironmentByIdOrDefault(applicationId, applicationEnvironmentId);
        if (!environment.getApplicationId().equals(applicationId)) {
            throw new NotFoundException("Unable to find environment with id <" + applicationEnvironmentId + "> for application <" + applicationId + ">");
        }
        // Security check user must be authorized to deploy the environment (or be application manager)
        AuthorizationUtil.checkAuthorizationForEnvironment(applicationService.getOrFail(applicationId), environment);

        Deployment deployment = deploymentService.getActiveDeploymentOrFail(environment.getId());
        DeploymentTopology deploymentTopology = deploymentRuntimeStateService.getRuntimeTopology(deployment.getId());
        return RestResponseBuilder.<TopologyDTO> builder().data(topologyDTOBuilder.initTopologyDTO(deploymentTopology, new TopologyDTO())).build();
    }

    private void validateCommand(OperationExecRequest operationRequest, Topology topology) throws ConstraintFunctionalException {
        NodeTemplate nodeTemplate = TopologyUtils.getNodeTemplate(topology.getId(), operationRequest.getNodeTemplateName(),
                TopologyUtils.getNodeTemplates(topology));
        NodeType indexedNodeType = toscaTypeSearchService.getRequiredElementInDependencies(NodeType.class, nodeTemplate.getType(), topology.getDependencies());

        Map<String, Interface> interfaces = IndexedModelUtils.mergeInterfaces(indexedNodeType.getInterfaces(), nodeTemplate.getInterfaces());

        if (interfaces == null || interfaces.get(operationRequest.getInterfaceName()) == null) {
            throw new NotFoundException("Interface [" + operationRequest.getInterfaceName() + "] not found in the node template ["
                    + operationRequest.getNodeTemplateName() + "] related to [" + indexedNodeType.getId() + "]");
        }

        Interface interfass = interfaces.get(operationRequest.getInterfaceName());

        validateOperation(interfass, operationRequest, topology.getDependencies());
    }

    private void validateParameters(Interface interfass, OperationExecRequest operationRequest, Set<CSARDependency> dependencies)
            throws ConstraintViolationException, ConstraintValueDoNotMatchPropertyTypeException, ConstraintRequiredParameterException {
        try {
            if (dependencies != null) {
                ToscaContext.init(dependencies);
            }
            ArrayList<String> missingParams = Lists.newArrayList();
            Operation operation = interfass.getOperations().get(operationRequest.getOperationName());

            if (operation.getInputParameters() != null) {
                for (Entry<String, IValue> inputParameter : operation.getInputParameters().entrySet()) {
                    if (inputParameter.getValue().isDefinition()) {
                        Object requestInputParameter = operationRequest.getParameters() == null ? null
                                : operationRequest.getParameters().get(inputParameter.getKey());
                        PropertyDefinition currentOperationParameter = (PropertyDefinition) inputParameter.getValue();
                        if (requestInputParameter != null) {
                            if (!(requestInputParameter instanceof Map)
                                    || !FunctionEvaluator.containGetSecretFunction(PropertyService.asFunctionPropertyValue(requestInputParameter))) {
                                // recover the good property definition for the current parameter
                                ConstraintPropertyService.checkPropertyConstraint(inputParameter.getKey(), requestInputParameter, currentOperationParameter);
                            }
                        } else if (currentOperationParameter.isRequired()) {
                            // input param not in the request, id required this is a missing parameter...
                            missingParams.add(inputParameter.getKey());
                        } else {
                            // set the value to null
                            operation.getInputParameters().put(inputParameter.getKey(), null);
                        }
                    }
                }
            }

            // check required input issue
            if (!missingParams.isEmpty()) {
                log.error("Missing required parameter", missingParams);
                ConstraintInformation constraintInformation = new ConstraintInformation(null, null, missingParams.toString(), "required");
                throw new ConstraintRequiredParameterException("Missing required parameters", null, constraintInformation);
            }
        } finally {
            if (ToscaContext.get() != null) {
                ToscaContext.destroy();
            }
        }
    }

    private void validateOperation(Interface interfass, OperationExecRequest operationRequest, Set<CSARDependency> dependencies)
            throws ConstraintFunctionalException {
        Operation operation = interfass.getOperations().get(operationRequest.getOperationName());
        if (operation == null) {
            throw new NotFoundException("Operation [" + operationRequest.getOperationName() + "] is not defined in the interface ["
                    + operationRequest.getInterfaceName() + "] of the node [" + operationRequest.getNodeTemplateName() + "]");
        }

        // validate parameters (value/type and required value)
        validateParameters(interfass, operationRequest, dependencies);
    }

    @ApiOperation(value = "Get non-natives node template of a topology.", notes = "Returns An map of non-natives {@link NodeTemplate}. Application role required [ APPLICATION_MANAGER | DEPLOYMENT_MANAGER ]")
    @RequestMapping(value = "/{applicationId:.+?}/environment/{applicationEnvironmentId:.+?}/nonNatives", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    public RestResponse<Map<String, NodeTemplate>> getNonNativesNodes(@PathVariable String applicationId, @PathVariable String applicationEnvironmentId) {
        Application application = applicationService.getOrFail(applicationId);
        ApplicationEnvironment environment = applicationEnvironmentService.getEnvironmentByIdOrDefault(applicationId, applicationEnvironmentId);
        AuthorizationUtil.checkAuthorizationForEnvironment(application, environment);

        Deployment deployment = deploymentService.getActiveDeploymentOrFail(environment.getId());
        DeploymentTopology deploymentTopology = deploymentRuntimeStateService.getRuntimeTopology(deployment.getId());

        Map<String, NodeTemplate> nonNativesNode = topologyTreeBuilderService.getNonNativesNodes(deploymentTopology);
        return RestResponseBuilder.<Map<String, NodeTemplate>> builder().data(nonNativesNode).build();
    }
}
