package alien4cloud.rest.orchestrator.resources;

import java.util.List;

import org.alien4cloud.tosca.exceptions.ConstraintValueDoNotMatchPropertyTypeException;
import org.alien4cloud.tosca.exceptions.ConstraintViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import alien4cloud.audit.annotation.Audit;
import alien4cloud.model.orchestrators.locations.LocationResourceTemplate;
import alien4cloud.model.orchestrators.locations.LocationResourceTemplateWithDependencies;
import alien4cloud.rest.model.RestResponse;
import alien4cloud.rest.model.RestResponseBuilder;
import alien4cloud.rest.orchestrator.AbstractLocationResourcesController;
import alien4cloud.rest.orchestrator.model.CreateLocationResourceTemplateRequest;
import alien4cloud.rest.orchestrator.model.UpdateLocationResourceTemplatePropertyRequest;
import alien4cloud.tosca.properties.constraints.ConstraintUtil;
import alien4cloud.tosca.properties.constraints.ConstraintUtil.ConstraintInformation;
import alien4cloud.utils.RestConstraintValidator;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import lombok.extern.slf4j.Slf4j;

/**
 * Controller that manages resources for orchestrator's locations.
 */
@Slf4j
@RestController
@RequestMapping(value = {"/rest/orchestrators/{orchestratorId}/locations/{locationId}/resources", "/rest/v1/orchestrators/{orchestratorId}/locations/{locationId}/resources", "/rest/latest/orchestrators/{orchestratorId}/locations/{locationId}/resources"}, produces = MediaType.APPLICATION_JSON_VALUE)
@Api(value = "Orchestrator Location Resources", description = "Manages locations for a given orchestrator.", authorizations = {
        @Authorization("ADMIN") }, position = 4400)
public class LocationResourcesController extends AbstractLocationResourcesController {

    @ApiOperation(value = "Add resource template to a location.", authorizations = { @Authorization("ADMIN") })
    @RequestMapping(method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('ADMIN')")
    @Audit
    public RestResponse<LocationResourceTemplateWithDependencies> addResourceTemplate(
            @ApiParam(value = "Id of the orchestrator for which to add resource template.", required = true) @PathVariable String orchestratorId,
            @ApiParam(value = "Id of the location of the orchestrator to add resource template.", required = true) @PathVariable String locationId,
            @RequestBody CreateLocationResourceTemplateRequest resourceTemplateRequest) {

        LocationResourceTemplateWithDependencies createdTemplate = locationResourceService.addResourceTemplateFromArchive(locationId, resourceTemplateRequest.getResourceName(),
                resourceTemplateRequest.getResourceType(), resourceTemplateRequest.getArchiveName(), resourceTemplateRequest.getArchiveVersion());
        return RestResponseBuilder.<LocationResourceTemplateWithDependencies> builder().data(createdTemplate).build();
    }

    @ApiOperation(value = "Duplicate a location resource template on a location.", authorizations = { @Authorization("ADMIN") })
    @RequestMapping(value = "/{id}/duplicate", method = RequestMethod.POST)
    @PreAuthorize("hasAuthority('ADMIN')")
    @Audit
    public RestResponse<LocationResourceTemplateWithDependencies> duplicateResourceTemplate(
            @ApiParam(value = "Id of the orchestrator for which to duplicate resource template.", required = true) @PathVariable String orchestratorId,
            @ApiParam(value = "Id of the location of the orchestrator to duplicate resource template.", required = true) @PathVariable String locationId,
            @ApiParam(value = "Id of the location's resource.", required = true) @PathVariable String id) {
        LocationResourceTemplateWithDependencies duplicatedTemplate = locationResourceService.duplicateResourceTemplate(id);
        return RestResponseBuilder.<LocationResourceTemplateWithDependencies> builder().data(duplicatedTemplate).build();
    }

    @ApiOperation(value = "Update location's resource's capability template capability property.", authorizations = { @Authorization("ADMIN") })
    @RequestMapping(value = "/{id}/template/capabilities/{capabilityName}/properties", method = RequestMethod.POST)
    @PreAuthorize("hasAuthority('ADMIN')")
    @Audit
    public RestResponse<ConstraintInformation> updateResourceTemplateCapabilityProperty(
            @ApiParam(value = "Id of the orchestrator for which to update resource template capability property.", required = true) @PathVariable String orchestratorId,
            @ApiParam(value = "Id of the location of the orchestrator to update resource template capability property.", required = true) @PathVariable String locationId,
            @ApiParam(value = "Id of the location's resource.", required = true) @PathVariable String id,
            @ApiParam(value = "Id of the location's resource template capability.", required = true) @PathVariable String capabilityName,
            @RequestBody UpdateLocationResourceTemplatePropertyRequest updateRequest) {
        try {
            locationResourceService.setTemplateCapabilityProperty(id, capabilityName, updateRequest.getPropertyName(), updateRequest.getPropertyValue());
            return RestResponseBuilder.<ConstraintUtil.ConstraintInformation> builder().build();
        } catch (ConstraintViolationException | ConstraintValueDoNotMatchPropertyTypeException e) {
            return RestConstraintValidator.fromException(e, updateRequest.getPropertyName(), updateRequest.getPropertyValue());
        }
    }

    @ApiOperation(value = "Auto configure the resources, if the location configurator plugin provides a way for.", authorizations = { @Authorization("ADMIN") })
    @RequestMapping(value = "/auto-configure", method = RequestMethod.GET)
    @PreAuthorize("hasAuthority('ADMIN')")
    public RestResponse<List<LocationResourceTemplate>> autoConfigureResources(
            @ApiParam(value = "Id of the orchestrator for which to Auto configure the resources.", required = true) @PathVariable String orchestratorId,
            @ApiParam(value = "Id of the location of the orchestrator to Auto configure the resources.", required = true) @PathVariable String locationId) throws UnsupportedOperationException {
        locationResourceService.deleteGeneratedResources(locationId);
        List<LocationResourceTemplate> generatedResources = locationResourceService.autoConfigureResources(locationId);
        return RestResponseBuilder.<List<LocationResourceTemplate>> builder().data(generatedResources).build();
    }
}
