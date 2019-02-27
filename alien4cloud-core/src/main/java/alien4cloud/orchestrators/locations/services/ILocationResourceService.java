package alien4cloud.orchestrators.locations.services;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.alien4cloud.tosca.exceptions.ConstraintValueDoNotMatchPropertyTypeException;
import org.alien4cloud.tosca.exceptions.ConstraintViolationException;
import org.alien4cloud.tosca.model.CSARDependency;

import alien4cloud.model.orchestrators.locations.AbstractLocationResourceTemplate;
import alien4cloud.model.orchestrators.locations.Location;
import alien4cloud.model.orchestrators.locations.LocationResourceTemplate;
import alien4cloud.model.orchestrators.locations.LocationResourceTemplateWithDependencies;
import alien4cloud.model.orchestrators.locations.LocationResources;
import alien4cloud.model.orchestrators.locations.PolicyLocationResourceTemplate;
import alien4cloud.orchestrators.plugin.ILocationResourceAccessor;
import alien4cloud.plugin.aop.Overridable;

@Overridable
public interface ILocationResourceService {

    /**
     * Get the list of resources definitions for a given location. PP.
     *
     * @param location the location.
     * @return A list of resource definitions for the given location.
     */
    LocationResources getLocationResources(Location location);

    /**
     * Get the list of resources definitions for a given orchestrator. PP.
     *
     * @param location the location.
     * @return A list of resource definitions for the given location.
     */
    LocationResources getLocationResourcesFromOrchestrator(Location location);

    /**
     * Auto configure resources for the given location. PP.
     *
     * @param locationId Id of the location.
     */
    List<LocationResourceTemplate> autoConfigureResources(String locationId) throws UnsupportedOperationException;

    /**
     * @deprecated Use {@link #addResourceTemplateFromArchive(String, String, String, String, String)} instead. PP.
     */
    @Deprecated
    LocationResourceTemplateWithDependencies addResourceTemplate(String locationId, String resourceName, String resourceTypeName);

    /**
     * Create a new resource template, getting its type from the given archive.
     * If the archive was not in the location dependencies (e.g. the template is a custom resources), update the location dependencies
     * accordingly and shout back the newly added dependencies.
     *
     * @param locationId The location to add the template to.
     * @param resourceName The name of the created resource template.
     * @param resourceTypeName The type of the template.
     * @param archiveName The name of the archive to find the type into.
     * @param archiveVersion The archive's version.
     * @return A wrapper object containing the {@link LocationResourceTemplate} along with a Set of {@link CSARDependency}
     */
    LocationResourceTemplateWithDependencies addResourceTemplateFromArchive(String locationId, String resourceName, String resourceTypeName, String archiveName,
            String archiveVersion);

    /**
     * Duplicates a location resource template
     * 
     * @param resourceId
     * @return
     */
    LocationResourceTemplateWithDependencies duplicateResourceTemplate(String resourceId);

    LocationResourceTypes getLocationResourceTypes(Collection<LocationResourceTemplate> resourceTemplates);

    LocationResourceTypes getPoliciesLocationResourceTypes(Collection<PolicyLocationResourceTemplate> resourceTemplates);

    /**
     * Create an instance of an ILocationResourceAccessor that will perform queries on LocationResourceTemplate for a given location.
     *
     * @param locationId Id of the location for which to get the accessor.
     * @return An instance of the ILocationResourceAccessor.
     */
    ILocationResourceAccessor accessor(String locationId);

    List<LocationResourceTemplate> getResourcesTemplates(String locationId);

    void deleteResourceTemplate(String resourceId);

    <T extends AbstractLocationResourceTemplate> T getOrFail(String resourceId);

    void merge(Object mergeRequest, String resourceId);

    void setTemplateProperty(String resourceId, String propertyName, Object propertyValue)
            throws ConstraintValueDoNotMatchPropertyTypeException, ConstraintViolationException;

    void setTemplateCapabilityProperty(String resourceId, String capabilityName, String propertyName, Object propertyValue)
            throws ConstraintViolationException, ConstraintValueDoNotMatchPropertyTypeException;

    /**
     * Delete all generated {@link LocationResourceTemplate} for a given location
     *
     * @param locationId
     */
    void deleteGeneratedResources(String locationId);

    void saveResource(Location location, AbstractLocationResourceTemplate resourceTemplate);

    void saveResource(AbstractLocationResourceTemplate resourceTemplate);

    void fillLocationResourceTypes(Collection<String> exposedTypes, LocationResourceTypes locationResourceTypes, Set<CSARDependency> dependencies);

    /**
     * Create a new policy template, getting its type from the given archive.
     * If the archive was not in the location dependencies (e.g. the template is a custom resources), update the location dependencies
     * accordingly and shout back the newly added dependencies.
     *
     * @param locationId The location to add the template to.
     * @param resourceName The name of the created policy template.
     * @param policyType The type of the template.
     * @param archiveName The name of the archive to find the type into.
     * @param archiveVersion The archive's version.
     * @return A wrapper object containing the {@link LocationResourceTemplate} along with a Set of {@link CSARDependency}
     */
    LocationResourceTemplateWithDependencies addPolicyTemplateFromArchive(String locationId, String resourceName, String policyType, String archiveName,
            String archiveVersion);

    LocationResourceTemplateWithDependencies duplicatePolicyTemplate(String resourceId);
}