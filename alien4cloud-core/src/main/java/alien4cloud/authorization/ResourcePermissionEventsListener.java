package alien4cloud.authorization;

import alien4cloud.dao.IGenericSearchDAO;
import alien4cloud.dao.model.GetMultipleDataResult;
import alien4cloud.model.orchestrators.locations.LocationResourceTemplate;
import alien4cloud.security.AbstractSecurityEnabledResource;
import alien4cloud.security.Subject;
import alien4cloud.security.event.GroupDeletedEvent;
import alien4cloud.security.event.UserDeletedEvent;
import alien4cloud.utils.TypeScanner;
import com.google.common.collect.Sets;
import org.alien4cloud.alm.events.AfterPermissionRevokedEvent;
import org.alien4cloud.alm.events.BeforeApplicationDeleted;
import org.alien4cloud.alm.events.BeforeApplicationEnvironmentDeleted;
import org.alien4cloud.alm.events.BeforeApplicationEnvironmentTypeDeleted;
import org.apache.commons.lang3.ArrayUtils;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Listeners on events that might affects resources permissions
 */
@Component
public class ResourcePermissionEventsListener {

    @Resource(name = "alien-es-dao")
    private IGenericSearchDAO alienDAO;

    @Resource
    private ResourcePermissionService resourcePermissionService;

    @EventListener
    public void userDeletedEventListener(UserDeletedEvent event) throws IOException, ClassNotFoundException {
        deleteUserPermissionOn(event.getUser().getUsername());
    }

    @EventListener
    public void groupDeletedEventListener(GroupDeletedEvent event) throws IOException, ClassNotFoundException {
        deleteGroupPermissionOn(event.getGroup().getId());
    }

    @EventListener
    public void applicationDeletedEventListener(BeforeApplicationDeleted event) throws IOException, ClassNotFoundException {
        deleteApplicationPermissionOn(event.getApplicationId());
    }

    @EventListener
    public void environmentDeletedEventListener(BeforeApplicationEnvironmentDeleted event) throws IOException, ClassNotFoundException {
        deleteEnvironmentPermissionOn(event.getApplicationEnvironmentId());
    }

    @EventListener
    public void environmentTypeDeletedEventListener(BeforeApplicationEnvironmentTypeDeleted event) throws IOException, ClassNotFoundException {
        deleteEnvironmentTypePermissionOn(event.getApplicationEnvironmentType());
    }

    @EventListener(condition = "#event.subjectType.toString() == 'USER' && #event.on.clazz.simpleName == 'Location'")
    public void userPermissionRevokedOnLocationEventListener(AfterPermissionRevokedEvent event) throws IOException, ClassNotFoundException {
        for (String username : event.getSubjects()) {
            deleteUserPermissionOn(username, LocationResourceTemplate.class);
        }
    }

    @EventListener(condition = "#event.subjectType.toString() == 'GROUP' && #event.on.clazz.simpleName == 'Location'")
    public void groupPermissionRevokedOnLocationEventListener(AfterPermissionRevokedEvent event) throws IOException, ClassNotFoundException {
        for (String subject : event.getSubjects()) {
            deleteGroupPermissionOn(subject, LocationResourceTemplate.class);
        }
    }

    @EventListener(condition = "#event.subjectType.toString() == 'APPLICATION' && #event.on.clazz.simpleName == 'Location'")
    public void applicationPermissionRevokedOnLocationEventListener(AfterPermissionRevokedEvent event) throws IOException, ClassNotFoundException {
        for (String subject : event.getSubjects()) {
            deleteApplicationPermissionOn(subject, LocationResourceTemplate.class);
        }
    }

    @EventListener(condition = "#event.subjectType.toString() == 'ENVIRONMENT' && #event.on.clazz.simpleName == 'Location'")
    public void environmentPermissionRevokedOnLocationEventListener(AfterPermissionRevokedEvent event) throws IOException, ClassNotFoundException {
        for (String subject : event.getSubjects()) {
            deleteEnvironmentPermissionOn(subject, LocationResourceTemplate.class);
        }
    }

    @EventListener(condition = "#event.subjectType.toString() == 'ENVIRONMENT_TYPE' && #event.on.clazz.simpleName == 'Location'")
    public void environmentTypePermissionRevokedOnLocationEventListener(AfterPermissionRevokedEvent event) throws IOException, ClassNotFoundException {
        for (String subject : event.getSubjects()) {
            deleteEnvironmentTypePermissionOn(subject, LocationResourceTemplate.class);
        }
    }

    private void deleteUserPermissionOn(String username, Class<?>... resourceClasses) throws IOException, ClassNotFoundException {
        FilterBuilder resourceFilter = FilterBuilders.nestedFilter("userPermissions", FilterBuilders.termFilter("userPermissions.key", username));
        deletePermissions(resourceFilter, username, ((resource, subjectId) -> resourcePermissionService.revokePermission(resource, Subject.USER, subjectId)),
                resourceClasses);
    }

    private void deleteGroupPermissionOn(String groupId, Class<?>... resourceClasses) throws IOException, ClassNotFoundException {
        FilterBuilder resourceFilter = FilterBuilders.nestedFilter("groupPermissions", FilterBuilders.termFilter("groupPermissions.key", groupId));
        deletePermissions(resourceFilter, groupId, ((resource, subjectId) -> resourcePermissionService.revokePermission(resource, Subject.GROUP, subjectId)),
                resourceClasses);
    }

    private void deleteApplicationPermissionOn(String applicationId, Class<?>... resourceClasses) throws IOException, ClassNotFoundException {
        FilterBuilder resourceFilter = FilterBuilders.nestedFilter("applicationPermissions",
                FilterBuilders.termFilter("applicationPermissions.key", applicationId));
        deletePermissions(resourceFilter, applicationId,
                ((resource, subjectId) -> resourcePermissionService.revokePermission(resource, Subject.APPLICATION, subjectId)), resourceClasses);
    }

    private void deleteEnvironmentPermissionOn(String environmentId, Class<?>... resourceClasses) throws IOException, ClassNotFoundException {
        FilterBuilder resourceFilter = FilterBuilders.nestedFilter("environmentPermissions",
                FilterBuilders.termFilter("environmentPermissions.key", environmentId));
        deletePermissions(resourceFilter, environmentId,
                ((resource, subjectId) -> resourcePermissionService.revokePermission(resource, Subject.ENVIRONMENT, subjectId)), resourceClasses);
    }

    private void deleteEnvironmentTypePermissionOn(String environmentId, Class<?>... resourceClasses) throws IOException, ClassNotFoundException {
        FilterBuilder resourceFilter = FilterBuilders.nestedFilter("environmentTypePermissions",
                FilterBuilders.termFilter("environmentTypePermissions.key", environmentId));
        deletePermissions(resourceFilter, environmentId,
                ((resource, subjectId) -> resourcePermissionService.revokePermission(resource, Subject.ENVIRONMENT_TYPE, subjectId)), resourceClasses);
    }

    private interface ResourcePermissionCleaner {
        void cleanPermission(AbstractSecurityEnabledResource resource, String subjectId);
    }

    private void deletePermissions(FilterBuilder appFilter, String ownerId, ResourcePermissionCleaner permissionCleaner, Class<?>... onClazzes)
            throws IOException, ClassNotFoundException {
        int from = 0;
        long totalResult;

        Set<Class<?>> classes = ArrayUtils.isNotEmpty(onClazzes) ? Sets.newHashSet(onClazzes)
                : TypeScanner.scanTypes("alien4cloud.model", AbstractSecurityEnabledResource.class);
        Set<String> indices = classes.stream().map(clazz -> alienDAO.getIndexForType(clazz)).collect(Collectors.toSet());
        do {
            GetMultipleDataResult<Object> result = alienDAO.search(indices.toArray(new String[indices.size()]), classes.toArray(new Class<?>[classes.size()]),
                    null, null, appFilter, null, from, 20);
            Arrays.stream(result.getData()).forEach(resource -> permissionCleaner.cleanPermission((AbstractSecurityEnabledResource) resource, ownerId));
            from += result.getData().length;
            totalResult = result.getTotalResults();
        } while (from < totalResult);
    }
}
