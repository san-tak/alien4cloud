package org.alien4cloud.exception.rest;

import java.util.List;

import javax.annotation.Resource;

import org.alien4cloud.alm.service.exceptions.IncompatibleHalfRelationshipException;
import org.alien4cloud.alm.service.exceptions.ServiceUsageException;
import org.alien4cloud.secret.exception.SecretProviderAuthenticationException;
import org.alien4cloud.tosca.editor.exception.CapabilityBoundException;
import org.alien4cloud.tosca.editor.exception.EditionConcurrencyException;
import org.alien4cloud.tosca.editor.exception.EditorToscaYamlParsingException;
import org.alien4cloud.tosca.editor.exception.PropertyValueException;
import org.alien4cloud.tosca.editor.exception.RecoverTopologyException;
import org.alien4cloud.tosca.editor.exception.RequirementBoundException;
import org.alien4cloud.tosca.editor.exception.UnmatchedElementPatternException;
import org.alien4cloud.tosca.editor.operations.RecoverTopologyOperation;
import org.alien4cloud.tosca.exceptions.ConstraintFunctionalException;
import org.alien4cloud.tosca.exceptions.ConstraintValueDoNotMatchPropertyTypeException;
import org.alien4cloud.tosca.exceptions.ConstraintViolationException;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.springframework.expression.ExpressionException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.google.common.collect.Lists;

import alien4cloud.component.repository.exception.RepositoryTechnicalException;
import alien4cloud.deployment.exceptions.InvalidDeploymentSetupException;
import alien4cloud.exception.AlreadyExistException;
import alien4cloud.exception.ApplicationVersionNotFoundException;
import alien4cloud.exception.CyclicReferenceException;
import alien4cloud.exception.DeleteDeployedException;
import alien4cloud.exception.DeleteLastApplicationEnvironmentException;
import alien4cloud.exception.DeleteLastApplicationVersionException;
import alien4cloud.exception.DeleteReferencedObjectException;
import alien4cloud.exception.GitConflictException;
import alien4cloud.exception.GitException;
import alien4cloud.exception.GitMergingStateException;
import alien4cloud.exception.GitStateException;
import alien4cloud.exception.IndexingServiceException;
import alien4cloud.exception.InvalidArgumentException;
import alien4cloud.exception.InvalidNameException;
import alien4cloud.exception.LocationSupportException;
import alien4cloud.exception.MissingCSARDependenciesException;
import alien4cloud.exception.NotFoundException;
import alien4cloud.exception.ReferencedResourceException;
import alien4cloud.exception.ReleaseReferencingSnapshotException;
import alien4cloud.exception.VersionConflictException;
import alien4cloud.exception.VersionRenameNotPossibleException;
import alien4cloud.images.exception.ImageUploadException;
import alien4cloud.model.common.Usage;
import alien4cloud.model.components.IncompatiblePropertyDefinitionException;
import alien4cloud.paas.exception.ComputeConflictNameException;
import alien4cloud.paas.exception.EmptyMetaPropertyException;
import alien4cloud.paas.exception.MissingPluginException;
import alien4cloud.paas.exception.OrchestratorDeploymentIdConflictException;
import alien4cloud.paas.exception.PaaSDeploymentException;
import alien4cloud.paas.exception.PaaSDeploymentIOException;
import alien4cloud.paas.exception.PaaSUndeploymentException;
import alien4cloud.paas.wf.exception.BadWorkflowOperationException;
import alien4cloud.rest.csar.CsarUploadResult;
import alien4cloud.rest.csar.CsarUploadUtil;
import alien4cloud.rest.model.RestErrorBuilder;
import alien4cloud.rest.model.RestErrorCode;
import alien4cloud.rest.model.RestResponse;
import alien4cloud.rest.model.RestResponseBuilder;
import alien4cloud.security.spring.Alien4CloudAccessDeniedHandler;
import alien4cloud.topology.exception.UpdateTopologyException;
import alien4cloud.tosca.properties.constraints.ConstraintUtil;
import alien4cloud.utils.RestConstraintValidator;
import alien4cloud.utils.version.InvalidVersionException;
import alien4cloud.utils.version.UpdateApplicationVersionException;
import lombok.extern.slf4j.Slf4j;

/**
 * All technical (runtime) exception handler goes here. It's unexpected exception and is in general back-end exception or bug in our code
 *
 * @author mkv
 */
@Slf4j
@ControllerAdvice
public class RestTechnicalExceptionHandler {

    @Resource
    private Alien4CloudAccessDeniedHandler accessDeniedHandler;

    private void logRestException(String message, Exception e) {
        if (log.isDebugEnabled()) {
            log.debug(message, e);
        } else {
            log.warn(message, e.getMessage());
        }
    }

    @ExceptionHandler(PropertyValueException.class)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @ResponseBody
    public RestResponse<ConstraintUtil.ConstraintInformation> invalidPropertyValue(PropertyValueException e) {
        if (log.isDebugEnabled()) {
            log.debug("Message: {}, property name: {}, property value: {}", e.getMessage(), e.getPropertyName(), e.getPropertyValue(), e);
        } else {
            log.warn("Message: {}, property name: {}, property value: {}", e.getMessage(), e.getPropertyName(), e.getPropertyValue(), e.getMessage());
        }
        return RestConstraintValidator.fromException((ConstraintFunctionalException) e.getCause(), e.getPropertyName(), e.getPropertyValue());
    }

    @ExceptionHandler(InvalidDeploymentSetupException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public RestResponse<Void> processInvalidDeploymentSetup(InvalidDeploymentSetupException e) {
        return RestResponseBuilder.<Void> builder()
                .error(RestErrorBuilder.builder(RestErrorCode.INVALID_DEPLOYMENT_SETUP).message("The deployment setup is invalid.").build()).build();
    }

    @ExceptionHandler(GitException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public RestResponse<Void> gitException(GitException e) {
        logRestException("Failed to import archive from git location.", e);
        String message = (e.getCause() == null) ? e.getMessage() : e.getMessage() + ":" + e.getCause();
        return RestResponseBuilder.<Void> builder().error(RestErrorBuilder.builder(RestErrorCode.GIT_IMPORT_FAILED).message(message).build()).build();
    }

    @ExceptionHandler(value = { GitMergingStateException.class, GitConflictException.class })
    @ResponseStatus(HttpStatus.CONFLICT)
    @ResponseBody
    public RestResponse<Void> gitConflictException(GitConflictException e) {
        logRestException("Failed to execute git operation due to conflict issue.", e);
        return RestResponseBuilder.<Void> builder().error(RestErrorBuilder.builder(RestErrorCode.GIT_CONFLICT_ERROR).message(e.getMessage()).build()).build();
    }

    @ExceptionHandler(GitStateException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public RestResponse<Void> gitStateException(GitStateException e) {
        logRestException("The git repository is not in a safe state.", e);
        return RestResponseBuilder.<Void> builder().error(RestErrorBuilder.builder(RestErrorCode.GIT_STATE_ERROR).message(e.getMessage()).build()).build();
    }

    @ExceptionHandler(AlreadyExistException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    @ResponseBody
    public RestResponse<Void> processAlreadyExist(AlreadyExistException e) {
        return RestResponseBuilder.<Void> builder()
                .error(RestErrorBuilder.builder(RestErrorCode.ALREADY_EXIST_ERROR).message("The posted object already exist.").build()).build();
    }

    @ExceptionHandler(InvalidNameException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public RestResponse<?> invalidNameExceptionHandler(InvalidNameException e) {
        return RestResponseBuilder.builder().error(RestErrorBuilder.builder(RestErrorCode.INVALID_NAME).message(e.getMessage()).build()).build();
    }

    @ExceptionHandler(DeleteReferencedObjectException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    @ResponseBody
    public RestResponse<Void> processDeleteReferencedObject(DeleteReferencedObjectException e) {
        logRestException("Resource still referenced, thus not deletable.", e);
        return RestResponseBuilder.<Void> builder()
                .error(RestErrorBuilder.builder(RestErrorCode.DELETE_REFERENCED_OBJECT_ERROR).message(e.getMessage()).build()).build();
    }

    @ExceptionHandler(VersionRenameNotPossibleException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    @ResponseBody
    public RestResponse<Void> processDeleteReferencedObject(VersionRenameNotPossibleException e) {
        logRestException("Version is still referenced and cannot be renamed", e);
        return RestResponseBuilder.<Void> builder().error(RestErrorBuilder.builder(RestErrorCode.VERSION_USED).message(e.getMessage()).build()).build();
    }

    @ExceptionHandler(CyclicReferenceException.class)
    @ResponseStatus(HttpStatus.LOOP_DETECTED)
    @ResponseBody
    public RestResponse<Void> processDeleteReferencedObject(CyclicReferenceException e) {
        logRestException("A node type that references a topology can not be added in this topology", e);
        return RestResponseBuilder.<Void> builder()
                .error(RestErrorBuilder.builder(RestErrorCode.CYCLIC_TOPOLOGY_TEMPLATE_REFERENCE_ERROR).message(e.getMessage()).build()).build();
    }

    @ExceptionHandler(ReleaseReferencingSnapshotException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    @ResponseBody
    public RestResponse<Void> processDeleteReferencedObject(ReleaseReferencingSnapshotException e) {
        logRestException("Can no release this version since it references SNAPSHOTs", e);
        return RestResponseBuilder.<Void> builder().error(RestErrorBuilder.builder(RestErrorCode.RELEASE_REFERENCING_SNAPSHOT).message(e.getMessage()).build())
                .build();
    }

    @ExceptionHandler(value = MissingPluginException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public RestResponse<Void> missingPluginExceptionHandler(MissingPluginException e) {
        logRestException("PaaS provider plugin cannot be found while used on a cloud, this should not happens.", e);
        return RestResponseBuilder.<Void> builder().error(RestErrorBuilder.builder(RestErrorCode.MISSING_PLUGIN_ERROR)
                .message("The cloud plugin cannot be found. Make sure that the plugin is installed and enabled.").build()).build();
    }

    @ExceptionHandler(value = InvalidArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public RestResponse<Void> invalidArgumentErrorHandler(InvalidArgumentException e) {
        logRestException("Method argument is invalid", e);
        return RestResponseBuilder.<Void> builder()
                .error(RestErrorBuilder.builder(RestErrorCode.ILLEGAL_PARAMETER).message("Method argument is invalid " + e.getMessage()).build()).build();
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public RestResponse<FieldErrorDTO[]> processValidationError(MethodArgumentNotValidException e) {
        BindingResult result = e.getBindingResult();
        List<FieldErrorDTO> errors = Lists.newArrayList();
        for (FieldError fieldError : result.getFieldErrors()) {
            errors.add(new FieldErrorDTO(fieldError.getField(), fieldError.getCode()));
        }
        return RestResponseBuilder.<FieldErrorDTO[]> builder().data(errors.toArray(new FieldErrorDTO[errors.size()]))
                .error(RestErrorBuilder.builder(RestErrorCode.ILLEGAL_PARAMETER).message("Method argument is invalid " + e.getMessage()).build()).build();
    }

    @ExceptionHandler(value = IndexingServiceException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public RestResponse<Void> indexingServiceErrorHandler(IndexingServiceException e) {
        logRestException("Indexing service has encoutered unexpected error", e);
        return RestResponseBuilder.<Void> builder().error(RestErrorBuilder.builder(RestErrorCode.INDEXING_SERVICE_ERROR)
                .message("Indexing service has encoutered unexpected error " + e.getMessage()).build()).build();
    }

    @ExceptionHandler(value = RepositoryTechnicalException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public RestResponse<Void> repositryServiceErrorHandler(RepositoryTechnicalException e) {
        logRestException("Repository service has encoutered unexpected error", e);
        return RestResponseBuilder.<Void> builder().error(RestErrorBuilder.builder(RestErrorCode.REPOSITORY_SERVICE_ERROR)
                .message("Repository service has encoutered unexpected error " + e.getMessage()).build()).build();
    }

    @ExceptionHandler(value = UpdateTopologyException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public RestResponse<Void> updateTopologyErrorHandler(UpdateTopologyException e) {
        logRestException("A topology cannot be updated if it's released", e);
        return RestResponseBuilder.<Void> builder().error(RestErrorBuilder.builder(RestErrorCode.UPDATE_AN_RELEASED_TOPOLOGY_ERROR)
                .message("A topology cannot be updated if it's released " + e.getMessage()).build()).build();
    }

    @ExceptionHandler(value = ImageUploadException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public RestResponse<Void> imageUploadErrorHandler(ImageUploadException e) {
        logRestException("Image upload error", e);
        return RestResponseBuilder.<Void> builder()
                .error(RestErrorBuilder.builder(RestErrorCode.IMAGE_UPLOAD_ERROR).message("Image upload error " + e.getMessage()).build()).build();
    }

    @ExceptionHandler(value = NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ResponseBody
    public RestResponse<Void> notFoundErrorHandler(NotFoundException e) {
        logRestException("Resource not found", e);
        return RestResponseBuilder.<Void> builder().error(RestErrorBuilder.builder(RestErrorCode.NOT_FOUND_ERROR).message(e.getMessage()).build()).build();
    }

    @ExceptionHandler(value = VersionConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    @ResponseBody
    public RestResponse<Void> versionConflictHandler(VersionConflictException e) {
        log.debug("Version conflict", e);
        return RestResponseBuilder.<Void> builder().error(RestErrorBuilder.builder(RestErrorCode.VERSION_CONFLICT_ERROR).message(e.getMessage()).build())
                .build();
    }

    @ExceptionHandler(value = ComputeConflictNameException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public RestResponse<Void> paaSDeploymentErrorHandler(ComputeConflictNameException e) {
        logRestException("Error in PaaS Deployment, computer name conflict ", e);
        return RestResponseBuilder.<Void> builder()
                .error(RestErrorBuilder.builder(RestErrorCode.COMPUTE_CONFLICT_NAME).message("Compute name conflict " + e.getMessage()).build()).build();
    }

    @ExceptionHandler(value = OrchestratorDeploymentIdConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    @ResponseBody
    public RestResponse<Void> paaSDeploymentErrorHandler(OrchestratorDeploymentIdConflictException e) {
        logRestException("Error in PaaS Deployment, conflict with the generated deployment paaSId", e);
        return RestResponseBuilder.<Void> builder().error(RestErrorBuilder.builder(RestErrorCode.DEPLOYMENT_PAAS_ID_CONFLICT).message(e.getMessage()).build())
                .build();
    }

    @ExceptionHandler(value = BadWorkflowOperationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public RestResponse<Void> paaSDeploymentErrorHandler(BadWorkflowOperationException e) {
        logRestException("Operation on workflow not permited", e);
        return RestResponseBuilder.<Void> builder().error(RestErrorBuilder.builder(RestErrorCode.BAD_WORKFLOW_OPERATION).message(e.getMessage()).build())
                .build();
    }

    @ExceptionHandler(value = IncompatibleHalfRelationshipException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public RestResponse<Void> incompatibleRelationshipErrorHandler(IncompatibleHalfRelationshipException e) {
        logRestException("Incompatible relationship", e);
        return RestResponseBuilder.<Void> builder().error(RestErrorBuilder.builder(RestErrorCode.ILLEGAL_PARAMETER).message(e.getMessage()).build()).build();
    }

    @ExceptionHandler(value = PaaSDeploymentException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public RestResponse<Void> paaSDeploymentErrorHandler(PaaSDeploymentException e) {
        logRestException("Error in PaaS Deployment", e);
        RestErrorCode errorCode = RestErrorCode.APPLICATION_DEPLOYMENT_ERROR;
        if (e.getPassErrorCode() != null) {
            errorCode = e.getPassErrorCode();
        }
        return RestResponseBuilder.<Void> builder()
                .error(RestErrorBuilder.builder(errorCode).message("Application cannot be deployed " + e.getMessage()).build()).build();
    }

    @ExceptionHandler(value = PaaSDeploymentIOException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public RestResponse<Void> paaSDeploymentIOErrorHandler(PaaSDeploymentIOException e) {
        logRestException("Error in PaaS Deployment", e);
        return RestResponseBuilder.<Void> builder().error(RestErrorBuilder.builder(RestErrorCode.APPLICATION_DEPLOYMENT_IO_ERROR)
                .message("Cannot reach the PaaS Manager endpoint " + e.getMessage()).build()).build();
    }

    @ExceptionHandler(value = PaaSUndeploymentException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public RestResponse<Void> paaSUndeploymentErrorHandler(PaaSUndeploymentException e) {
        logRestException("Error in UnDeployment", e);
        return RestResponseBuilder.<Void> builder().error(
                RestErrorBuilder.builder(RestErrorCode.APPLICATION_UNDEPLOYMENT_ERROR).message("Application cannot be undeployed " + e.getMessage()).build())
                .build();
    }

    @ExceptionHandler(value = AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    @ResponseBody
    public RestResponse<Void> accessDeniedHandler(AccessDeniedException e) {
        return accessDeniedHandler.getUnauthorizedRestError();
    }

    @ExceptionHandler(value = Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public RestResponse<String> catchAllErrorHandler(Exception e) {
        logRestException("Uncategorized error", e);
        String stackTrace = ExceptionUtils.getFullStackTrace(e);
        return RestResponseBuilder.<String> builder().data(stackTrace)
                .error(RestErrorBuilder.builder(RestErrorCode.UNCATEGORIZED_ERROR).message(e.getMessage()).build()).build();
    }

    @ExceptionHandler(value = InvalidVersionException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public RestResponse<Void> applicationVersionErrorHandler(InvalidVersionException e) {
        logRestException("Application version error", e);
        return RestResponseBuilder.<Void> builder()
                .error(RestErrorBuilder.builder(RestErrorCode.APPLICATION_VERSION_ERROR).message("Application version error : " + e.getMessage()).build())
                .build();
    }

    @ExceptionHandler(value = UpdateApplicationVersionException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public RestResponse<Void> updteApplicationVersionErrorHandler(UpdateApplicationVersionException e) {
        logRestException("Update application version error", e);
        return RestResponseBuilder.<Void> builder().error(RestErrorBuilder.builder(RestErrorCode.UPDATE_RELEASED_APPLICATION_VERSION_ERROR)
                .message("Update application version error : " + e.getMessage()).build()).build();
    }

    @ExceptionHandler(value = DeleteLastApplicationEnvironmentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public RestResponse<Void> deleteLastApplicationEnvironmentErrorHandler(DeleteLastApplicationEnvironmentException e) {
        logRestException("Delete last application environment error", e);
        return RestResponseBuilder.<Void> builder().error(
                RestErrorBuilder.builder(RestErrorCode.APPLICATION_ENVIRONMENT_ERROR).message("Application environment error : " + e.getMessage()).build())
                .build();
    }

    @ExceptionHandler(value = DeleteLastApplicationVersionException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public RestResponse<Void> deleteLastApplicationBersionErrorHandler(DeleteLastApplicationVersionException e) {
        logRestException("Delete last application version error", e);
        return RestResponseBuilder.<Void> builder()
                .error(RestErrorBuilder.builder(RestErrorCode.LAST_APPLICATION_VERSION_ERROR).message("Application version error : " + e.getMessage()).build())
                .build();
    }

    @ExceptionHandler(value = DeleteDeployedException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public RestResponse<Void> deleteDeployedErrorHandler(DeleteDeployedException e) {
        logRestException("Delete deployed element error", e);
        return RestResponseBuilder.<Void> builder().error(RestErrorBuilder.builder(RestErrorCode.APPLICATION_ENVIRONMENT_DEPLOYED_ERROR)
                .message("Application environment delete error : " + e.getMessage()).build()).build();
    }

    @ExceptionHandler(value = ExpressionException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public RestResponse<Void> generatePaasIdErrorHandler(ExpressionException e) {
        logRestException("Problem parsing right operand during the generation of PaasId : ", e);
        return RestResponseBuilder.<Void> builder().error(
                RestErrorBuilder.builder(RestErrorCode.DEPLOYMENT_NAMING_POLICY_ERROR).message("Problem parsing right operand : " + e.getMessage()).build())
                .build();
    }

    @ExceptionHandler(value = EmptyMetaPropertyException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public RestResponse<Void> generateEmptyMetaPropertyErrorHandler(EmptyMetaPropertyException e) {
        logRestException("One of meta property is empty and don't have a default value : ", e);
        return RestResponseBuilder.<Void> builder().error(RestErrorBuilder.builder(RestErrorCode.EMPTY_META_PROPERTY_ERROR)
                .message("One of meta property is empty and don't have a default value : " + e.getMessage()).build()).build();
    }

    @ExceptionHandler(value = ApplicationVersionNotFoundException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public RestResponse<Void> applicationVersionIsMissingErrorHandler(ApplicationVersionNotFoundException e) {
        logRestException("App version is missing", e);
        return RestResponseBuilder.<Void> builder()
                .error(RestErrorBuilder.builder(RestErrorCode.MISSING_APPLICATION_VERSION_ERROR).message(e.getMessage()).build()).build();
    }

    @ExceptionHandler(value = ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public RestResponse<Void> metaPropertyConstraintViolationErrorHandler(ConstraintViolationException e) {
        logRestException("Constraint violation error for property", e);
        return RestResponseBuilder.<Void> builder()
                .error(RestErrorBuilder.builder(RestErrorCode.PROPERTY_CONSTRAINT_VIOLATION_ERROR).message(e.getMessage()).build()).build();
    }

    @ExceptionHandler(value = ConstraintValueDoNotMatchPropertyTypeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public RestResponse<Void> constraintValueDoNotMatchPropertyErrorHandler(ConstraintValueDoNotMatchPropertyTypeException e) {
        logRestException("Constraint value do not match property", e);
        return RestResponseBuilder.<Void> builder().error(RestErrorBuilder.builder(RestErrorCode.PROPERTY_TYPE_VIOLATION_ERROR).message(e.getMessage()).build())
                .build();
    }

    @ExceptionHandler(value = IncompatiblePropertyDefinitionException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public RestResponse<Void> IncompatiblePropertyDefinitionExceptionHandler(IncompatiblePropertyDefinitionException e) {
        logRestException("Property definition doesn't match", e);
        return RestResponseBuilder.<Void> builder()
                .error(RestErrorBuilder.builder(RestErrorCode.PROPERTY_DEFINITION_MATCH_ERROR).message(e.getMessage()).build()).build();
    }

    @ExceptionHandler(value = MissingCSARDependenciesException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public RestResponse<Void> MissingCSARDependenciesExceptionHandler(MissingCSARDependenciesException e) {
        logRestException("The CSAR of the location doesn't have all dependencies", e);
        return RestResponseBuilder.<Void> builder().error(RestErrorBuilder.builder(RestErrorCode.CSAR_PARSING_ERROR).message(e.getMessage()).build()).build();
    }

    @ExceptionHandler(value = EditionConcurrencyException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public RestResponse<Void> handleEditionConcurrencyException(EditionConcurrencyException e) {
        return RestResponseBuilder.<Void> builder()
                .error(RestErrorBuilder.builder(RestErrorCode.EDITOR_CONCURRENCY_ERROR).message(
                        "Another user has changed the topology and your version is not consistent or topology edition session has expired. " + e.getMessage())
                        .build())
                .build();
    }

    @ExceptionHandler(value = UnmatchedElementPatternException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public RestResponse<Void> handleEditionConcurrencyException(UnmatchedElementPatternException e) {
        return RestResponseBuilder.<Void> builder()
                .error(RestErrorBuilder.builder(RestErrorCode.ELEMENT_NAME_PATTERN_CONSTRAINT).message(e.getMessage()).build()).build();
    }

    @ExceptionHandler(value = CapabilityBoundException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public RestResponse<Void> handleEditionConcurrencyException(CapabilityBoundException e) {
        return RestResponseBuilder.<Void> builder().error(RestErrorBuilder.builder(RestErrorCode.UPPER_BOUND_REACHED).message(e.getMessage()).build()).build();
    }

    @ExceptionHandler(value = RequirementBoundException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public RestResponse<Void> handleEditionConcurrencyException(RequirementBoundException e) {
        return RestResponseBuilder.<Void> builder().error(RestErrorBuilder.builder(RestErrorCode.UPPER_BOUND_REACHED).message(e.getMessage()).build()).build();
    }

    @ExceptionHandler(value = RecoverTopologyException.class)
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public RestResponse<RecoverTopologyOperation> handleTopologyRecoveryException(RecoverTopologyException e) {
        return RestResponseBuilder.<RecoverTopologyOperation> builder()
                .error(RestErrorBuilder.builder(RestErrorCode.RECOVER_TOPOLOGY).message(e.getMessage()).build()).data(e.getOperation()).build();

    }

    @ExceptionHandler(value = UnsupportedOperationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public RestResponse<Void> unsupportedOperationErrorHandler(UnsupportedOperationException e) {
        logRestException("Operation not supported", e);
        return RestResponseBuilder.<Void> builder().error(RestErrorBuilder.builder(RestErrorCode.UNSUPPORTED_OPERATION_ERROR).message(e.getMessage()).build())
                .build();
    }

    @ExceptionHandler(value = EditorToscaYamlParsingException.class)
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public RestResponse<CsarUploadResult> editorToscaYamlParsingException(EditorToscaYamlParsingException e) {
        logRestException("Error in topology tosca yaml detected", e);
        return RestResponseBuilder.<CsarUploadResult> builder().data(CsarUploadUtil.toUploadResult(e.getParsingResult()))
                .error(RestErrorBuilder.builder(RestErrorCode.CSAR_PARSING_ERROR).build()).build();
    }

    @ExceptionHandler(value = ServiceUsageException.class)
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public RestResponse<Usage[]> serviceUsageExceptionHandler(ServiceUsageException e) {
        logRestException("Error on service deletion", e);
        return RestResponseBuilder.<Usage[]> builder().data(e.getUsages())
                .error(RestErrorBuilder.builder(RestErrorCode.RESOURCE_USED_ERROR).message(e.getMessage()).build()).build();
    }

    @ExceptionHandler(value = ReferencedResourceException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    @ResponseBody
    public RestResponse<Usage[]> ReferencedResourceExceptionHandler(ReferencedResourceException e) {
        logRestException("Operation on referenced resource error", e);
        return RestResponseBuilder.<Usage[]> builder().data(e.getUsages())
                .error(RestErrorBuilder.builder(RestErrorCode.RESOURCE_USED_ERROR).message(e.getMessage()).build()).build();
    }

    @ExceptionHandler(value = LocationSupportException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public RestResponse<Void> locationSupportExceptionHandler(LocationSupportException e) {
        logRestException("Location support exception", e);
        return RestResponseBuilder.<Void> builder()
                .error(RestErrorBuilder.builder(RestErrorCode.ORCHESTRATOR_LOCATION_SUPPORT_VIOLATION).message(e.getMessage()).build()).build();
    }

    @ExceptionHandler(SecretProviderAuthenticationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public RestResponse<Void> secretVaultAuthenticationHandler(SecretProviderAuthenticationException e) {
        logRestException("Secret provider credentials is invalid", e);
        return RestResponseBuilder.<Void> builder()
                .error(RestErrorBuilder.builder(RestErrorCode.INVALID_SECRET_CREDENTIALS_EXCEPTION).message(e.getMessage()).build()).build();
    }
}
