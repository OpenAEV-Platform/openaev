package io.openbas.service;

import io.openbas.database.model.*;
import io.openbas.rest.exception.ElementNotFoundException;
import io.openbas.rest.inject.service.InjectService;
import io.openbas.rest.inject.service.InjectStatusService;
import jakarta.validation.constraints.NotNull;
import java.util.EnumSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class PermissionService {

  // TODO: today settings are necessary to login -> review that
  private static final EnumSet<ResourceType> RESOURCES_OPEN =
      EnumSet.of(
          ResourceType.PLAYER,
          ResourceType.TEAM,
          ResourceType.PLATFORM_SETTING,
          ResourceType.CVE,
          ResourceType.TAG,
          ResourceType.ATTACK_PATTERN,
          ResourceType.KILL_CHAIN_PHASE,
          ResourceType.ORGANIZATION,
          ResourceType.INJECTOR_CONTRACT); // TODO review open apis see issue/3789

  private static final EnumSet<ResourceType> RESOURCES_MANAGED_BY_GRANTS =
      EnumSet.of(
          ResourceType.SCENARIO,
          ResourceType.SIMULATION,
          ResourceType.SIMULATION_OR_SCENARIO,
          ResourceType.PAYLOAD,
          ResourceType.ATOMIC_TESTING);

  private static final EnumSet<ResourceType> RESOURCES_USING_PARENT_PERMISSION =
      EnumSet.of(ResourceType.INJECT, ResourceType.NOTIFICATION_RULE);

  private final GrantService grantService;
  private final InjectService injectService;
  private final InjectStatusService injectStatusService;
  private final NotificationRuleService notificationRuleService;

  @Transactional
  public boolean hasPermission(
      @NotNull final User user, String resourceId, ResourceType resourceType, Action action) {

    Set<Capability> userCapabilities = user.getCapabilities();

    // admin user or capa bypass
    if (user.isAdminOrBypass()) {
      return true;
    }

    // if for some reason we are not able to identify the resource we only allow admin
    if (ResourceType.UNKNOWN.equals(resourceType)) {
      return user.isAdmin();
    }

    // If we are searching for resources with parent permission, the search function must handle the
    // permission computation itself.
    // Example: export of injects
    if (RESOURCES_USING_PARENT_PERMISSION.contains(resourceType) && Action.SEARCH.equals(action)) {
      return true;
    }
    // for inject/article the permission will be based on the parent's (scenario/simulation/test)
    // permission
    if (RESOURCES_USING_PARENT_PERMISSION.contains(resourceType)) {
      Target parentTarget = resolveTarget(resourceId, resourceType, action);
      resourceId = parentTarget.resourceId;
      resourceType = parentTarget.resourceType;
      action = parentTarget.action;
    }

    // if resource is grantable then the search api is open as it will be filtered in the code
    if (RESOURCES_MANAGED_BY_GRANTS.contains(resourceType) && Action.SEARCH.equals(action)) {
      return true;
    }

    // check if the user has the capa first
    boolean hasPermission = hasCapaPermission(user, resourceType, action);
    if (hasCapaPermission(user, resourceType, action)) {
      return true;
    }

    // if the user doesn't have the capa check if the user has a grant
    if (!hasPermission || RESOURCES_MANAGED_BY_GRANTS.contains(resourceType)) {
      if (Action.DUPLICATE.equals(action)) {
        // to duplicate we need the "create" capa but also read on the resource
        return hasCapaPermission(user, resourceType, action)
            && hasGrantPermission(user, resourceId, resourceType, Action.READ);
      }

      return hasGrantPermission(user, resourceId, resourceType, action);
    }
    return false;
  }

  private boolean hasGrantPermission(
      @NotNull final User user,
      final String resourceId,
      @NotNull final ResourceType resourceType,
      @NotNull final Action action) {
    // user can access search apis but the result will be filtered
    if (Action.SEARCH.equals(action)) {
      return true;
    }

    switch (action) {
      case READ:
        return grantService.hasReadGrant(resourceId, user);
      case WRITE, DELETE:
        return grantService.hasWriteGrant(resourceId, user);
      case LAUNCH:
        return grantService.hasLaunchGrant(resourceId, user);
      default:
        return false;
    }
  }

  boolean hasCapaPermission(
      @NotNull final User user,
      @NotNull final ResourceType resourceType,
      @NotNull final Action action) {
    Set<Capability> userCapabilities = user.getCapabilities();

    if (userCapabilities.contains(Capability.BYPASS)) {
      return true;
    }

    if (RESOURCES_OPEN.contains(resourceType)
        && (Action.READ.equals(action) || Action.SEARCH.equals(action))) {
      return true;
    }

    Capability requiredCapability = Capability.of(resourceType, action).orElse(Capability.BYPASS);

    return userCapabilities.contains(requiredCapability);
  }

  private Target resolveTarget(
      @NotNull final String resourceId,
      @NotNull final ResourceType resourceType,
      @NotNull final Action action) {
    if (resourceType == ResourceType.INJECT) {
      Inject inject = injectService.inject(resourceId);
      // parent action rule: anything non-READ becomes WRITE on the parent
      Action parentAction = (action == Action.READ) ? Action.READ : Action.WRITE;
      return new Target(inject.getParentResourceId(), inject.getParentResourceType(), parentAction);
    } else if (resourceType == ResourceType.NOTIFICATION_RULE) {
      NotificationRule notificationRule =
          notificationRuleService
              .findById(resourceId)
              .orElseThrow(
                  () ->
                      new ElementNotFoundException(
                          "NotificationRule not found with id:" + resourceId));
      Action parentAction = Action.READ; // FIXME permission should be linked to userid
      return new Target(notificationRule.getResourceId(), ResourceType.SCENARIO, parentAction);
    }
    return new Target(resourceId, resourceType, action);
  }

  /** Used to return Parent resource information */
  private record Target(String resourceId, ResourceType resourceType, Action action) {}
}
