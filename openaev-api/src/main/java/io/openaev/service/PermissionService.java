package io.openaev.service;

import io.openaev.aop.AccessControlAspect;
import io.openaev.context.AmbientTenantBridge;
import io.openaev.context.TxCtx;
import io.openaev.database.model.*;
import io.openaev.database.model.autonomous.AutonomousRun;
import io.openaev.database.repository.EvaluationRepository;
import io.openaev.database.repository.ObjectiveRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.service.autonomous.AutonomousRunService;
import io.openaev.service.chaining.ConditionService;
import io.openaev.service.chaining.StepService;
import io.openaev.service.chaining.WorkflowService;
import jakarta.validation.constraints.NotNull;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
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
          ResourceType.VULNERABILITY,
          ResourceType.TAG,
          ResourceType.ATTACK_PATTERN,
          ResourceType.DOMAIN,
          ResourceType.KILL_CHAIN_PHASE,
          ResourceType.ORGANIZATION,
          // INJECTOR is open for READ/SEARCH because multiple views (e.g. threat arsenal)
          // need to list injectors for filtering, and injector names are not sensitive.
          ResourceType.INJECTOR,
          ResourceType.MAPPER,
          // NOTIFIER is open for READ/SEARCH so any user can pick notifiers when creating
          // notification triggers; write operations stay gated by tenant settings capabilities
          // and notifier_configuration is masked in outputs for users without those capabilities
          // (see NotifierApi).
          ResourceType.NOTIFIER,
          ResourceType.NEWS_FEED);

  private static final EnumSet<ResourceType> RESOURCES_MANAGED_BY_GRANTS =
      EnumSet.of(
          ResourceType.SCENARIO,
          ResourceType.SIMULATION,
          ResourceType.AUTONOMOUS_RUN,
          ResourceType.SIMULATION_OR_SCENARIO,
          ResourceType.THREAT_ARSENAL,
          ResourceType.ATOMIC_TESTING);

  private static final EnumSet<ResourceType> RESOURCES_USING_PARENT_PERMISSION =
      EnumSet.of(
          ResourceType.INJECT,
          ResourceType.INJECTOR_CONTRACT,
          ResourceType.OBJECTIVE,
          ResourceType.EVALUATION,
          ResourceType.WORKFLOW,
          ResourceType.STEP,
          ResourceType.CONDITION,
          ResourceType.AUTONOMOUS_RUN);

  private final GrantService grantService;
  private final InjectService injectService;
  private final AutonomousRunService autonomousRunService;
  private final ObjectiveRepository objectiveRepository;
  private final EvaluationRepository evaluationRepository;
  private final WorkflowService workflowService;
  private final StepService stepService;
  private final ConditionService conditionService;
  private final AmbientTenantBridge ambientTenantBridge;

  @Transactional
  public boolean hasPermission(
      @NotNull final User user,
      Optional<AccessControlAspect.HttpMappingInfo> httpMappingInfo,
      String resourceId,
      ResourceType resourceType,
      Action action) {
    return checkPermission(
        user,
        httpMappingInfo,
        resourceId,
        resourceType,
        action,
        () -> resolveTarget(resourceId, resourceType, action));
  }

  /**
   * Prepares a check of one resource that {@link #hasPermission(User, Optional, CheckedResource)}
   * then evaluates in each tenant of a request scope. Only the caller's capabilities and tenant
   * bypass follow the tenant of an evaluation. The parent of a sub-resource does not: loading it
   * goes through services that read through the ambient tenant and, for an autonomous run, settle
   * its status from its simulation. So the parent is resolved at most once, by the first evaluation
   * that needs it, and always in {@code resolutionTenant}.
   *
   * @param resolutionTenant the tenant ambient for the request, in which the parent is resolved
   */
  public CheckedResource checkedResource(
      String resolutionTenant, String resourceId, ResourceType resourceType, Action action) {
    return new CheckedResource(
        resourceId,
        resourceType,
        action,
        () ->
            ambientTenantBridge.callInTenant(
                resolutionTenant, () -> resolveTarget(resourceId, resourceType, action)));
  }

  /** The check of {@link #hasPermission(User, Optional, String, ResourceType, Action)}. */
  @Transactional
  public boolean hasPermission(
      @NotNull final User user,
      Optional<AccessControlAspect.HttpMappingInfo> httpMappingInfo,
      CheckedResource resource) {
    return checkPermission(
        user,
        httpMappingInfo,
        resource.resourceId,
        resource.resourceType,
        resource.action,
        resource::parent);
  }

  /**
   * The same check, run in the tenant {@code ctx} names. For callers no request binds a {@code
   * TxCtx} for, such as the stream listeners: resolving a parent permission loads the child row (an
   * inject, an autonomous run), which a v2-active table hides from an unscoped transaction.
   */
  @Transactional(readOnly = true)
  public boolean hasPermissionInTenant(
      TxCtx ctx,
      @NotNull final User user,
      String resourceId,
      ResourceType resourceType,
      Action action) {
    return checkPermission(
        user,
        Optional.empty(),
        resourceId,
        resourceType,
        action,
        () -> resolveTarget(resourceId, resourceType, action));
  }

  private boolean checkPermission(
      @NotNull final User user,
      Optional<AccessControlAspect.HttpMappingInfo> httpMappingInfo,
      String resourceId,
      ResourceType resourceType,
      Action action,
      Supplier<Target> parent) {

    // admin user bypasses all checks
    if (user.isAdmin()) {
      return true;
    }

    // BYPASS scope must match the scope of the requested resource:
    //  - platform BYPASS  +  platform-scoped resource  → OK
    //  - tenant   BYPASS  +  tenant-scoped   resource  → OK
    if (isBypassGranted(user, resourceType, action)) {
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
      Target parentTarget = parent.get();
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

    // check if the user
    if (hasPermission) {
      return true;
    }
    // if the user doesn't have the capa check if the user has a grant
    if (RESOURCES_MANAGED_BY_GRANTS.contains(resourceType)) {
      return hasGrantPermission(user, resourceId, resourceType, action);
    }

    // Specific case: /options endpoints are used to filter tables.
    // In the context of a grantable resource, they  should be accessible if the sourceId associated
    // with the request is a resource on which the user is granted
    if (httpMappingInfo.isEmpty()) {
      return false;
    }
    AccessControlAspect.HttpMappingInfo mappingInfo = httpMappingInfo.get();
    boolean endsWithOptions =
        Arrays.stream(mappingInfo.paths()).anyMatch(path -> path.endsWith("/options"));
    if (endsWithOptions) {
      // Retrieve the request param to check if a source ID is provided
      if (mappingInfo.args().containsKey("sourceId")) {
        String sourceId = mappingInfo.args().get("sourceId").toString();
        return hasGrantPermission(user, sourceId, resourceType, action);
      }
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

    if (isOpenResource(resourceType, action)) {
      return true;
    }

    return hasCapabilityPermission(user, resourceType, action);
  }

  /**
   * Checks admin/bypass/capability only, ignoring the open-resource shortcut. Used to decide
   * whether sensitive fields of open resources (e.g. notifier configuration) can be exposed to the
   * user, where the endpoint itself is open but the field is capability-gated.
   */
  public boolean hasCapabilityPermission(
      @NotNull final User user,
      @NotNull final ResourceType resourceType,
      @NotNull final Action action) {

    if (user.isAdmin()) {
      return true;
    }

    if (isBypassGranted(user, resourceType, action)) {
      return true;
    }

    Capability requiredCapability = Capability.of(resourceType, action).orElse(Capability.BYPASS);
    return user.getCapabilities().contains(requiredCapability);
  }

  /** Checks whether the user's BYPASS capability covers the requested resource scope. */
  private static boolean isBypassGranted(User user, ResourceType resourceType, Action action) {
    boolean hasPlatformBypass = user.hasPlatformBypass();
    boolean hasTenantBypass = user.hasTenantBypass();
    if (!hasPlatformBypass && !hasTenantBypass) {
      return false;
    }

    Capability requiredCapability = Capability.of(resourceType, action).orElse(null);
    if (requiredCapability == null) {
      // No mapped capability — any BYPASS is sufficient
      return true;
    }

    Set<CapabilityScope> scopes = requiredCapability.getScopes();
    if (scopes.contains(CapabilityScope.PLATFORM) && hasPlatformBypass) {
      return true;
    }
    if (scopes.contains(CapabilityScope.TENANT) && hasTenantBypass) {
      return true;
    }
    return false;
  }

  public boolean hasUserAdminPrivileges(User user) {
    return user.isAdminOrBypass();
  }

  private Target resolveTarget(
      @NotNull final String resourceId,
      @NotNull final ResourceType resourceType,
      @NotNull final Action action) {

    return switch (resourceType) {
      case AUTONOMOUS_RUN -> {
        AutonomousRun run = autonomousRunService.get(resourceId);
        // Parent action rule: READ stays READ, LAUNCH stays LAUNCH, the rest becomes WRITE.
        Action parentAction =
            switch (action) {
              case READ -> Action.READ;
              case LAUNCH -> Action.LAUNCH;
              default -> Action.WRITE;
            };
        yield new Target(run.getParentResourceId(), run.getParentResourceType(), parentAction);
      }
      case INJECT -> {
        Inject inject = injectService.inject(resourceId);
        // Parent action rule: READ stays READ, LAUNCH stays LAUNCH, the rest becomes WRITE.
        Action parentAction =
            switch (action) {
              case READ -> Action.READ;
              case LAUNCH -> Action.LAUNCH;
              default -> Action.WRITE;
            };
        yield new Target(
            inject.getParentResourceId(), inject.getParentResourceType(), parentAction);
      }
      case INJECTOR_CONTRACT -> new Target(resourceId, ResourceType.THREAT_ARSENAL, action);
      case OBJECTIVE -> {
        Objective objective =
            objectiveRepository
                .findById(resourceId)
                .orElseThrow(
                    () ->
                        new ElementNotFoundException("Objective not found with id: " + resourceId));
        // parent action rule: anything non-READ becomes WRITE on the parent
        Action parentAction = (action == Action.READ) ? Action.READ : Action.WRITE;
        yield new Target(
            objective.getParentResourceId(), objective.getParentResourceType(), parentAction);
      }
      case EVALUATION -> {
        Evaluation evaluation =
            evaluationRepository
                .findById(resourceId)
                .orElseThrow(
                    () ->
                        new ElementNotFoundException(
                            "Evaluation not found with id: " + resourceId));
        // parent action rule: anything non-READ becomes WRITE on the parent
        Action parentAction = (action == Action.READ) ? Action.READ : Action.WRITE;
        yield new Target(
            evaluation.getParentResourceId(), evaluation.getParentResourceType(), parentAction);
      }
      case WORKFLOW -> {
        Workflow workflow = workflowService.findById(resourceId);
        // delete/write on a workflow is treated as a write on its parent (simulation/scenario)
        Action parentAction = (action == Action.READ) ? Action.READ : Action.WRITE;
        yield resolveWorkflowTarget(workflow, parentAction);
      }
      case STEP -> {
        Step step = stepService.findById(resourceId);
        Action parentAction = (action == Action.READ) ? Action.READ : Action.WRITE;
        yield resolveWorkflowTarget(step.getWorkflow(), parentAction);
      }
      case CONDITION -> {
        Condition condition = conditionService.findConditionRootById(resourceId);
        Workflow workflow = workflowService.findById(condition.getWorkflowId());
        Action parentAction = (action == Action.READ) ? Action.READ : Action.WRITE;
        yield resolveWorkflowTarget(workflow, parentAction);
      }
      default -> new Target(resourceId, resourceType, action);
    };
  }

  private Target resolveWorkflowTarget(Workflow workflow, Action parentAction) {
    if (workflow.getSimulation() != null) {
      return new Target(workflow.getSimulation().getId(), ResourceType.SIMULATION, parentAction);
    }
    if (workflow.getScenario() != null) {
      return new Target(workflow.getScenario().getId(), ResourceType.SCENARIO, parentAction);
    }
    throw new ElementNotFoundException(
        "Workflow has no associated simulation or scenario: " + workflow.getId());
  }

  /** Used to return Parent resource information */
  private record Target(String resourceId, ResourceType resourceType, Action action) {}

  /** A resource prepared by {@link #checkedResource}, whose parent is resolved at most once. */
  public static final class CheckedResource {

    private final String resourceId;
    private final ResourceType resourceType;
    private final Action action;
    private final Supplier<Target> parentResolution;
    private Target parent;

    private CheckedResource(
        String resourceId,
        ResourceType resourceType,
        Action action,
        Supplier<Target> parentResolution) {
      this.resourceId = resourceId;
      this.resourceType = resourceType;
      this.action = action;
      this.parentResolution = parentResolution;
    }

    private Target parent() {
      if (parent == null) {
        parent = parentResolution.get();
      }
      return parent;
    }
  }

  public static boolean isManagedByGrants(ResourceType resourceType) {
    return RESOURCES_MANAGED_BY_GRANTS.contains(resourceType);
  }

  public static boolean isOpenResource(ResourceType resourceType, Action action) {
    return RESOURCES_OPEN.contains(resourceType)
        && (Action.READ.equals(action) || Action.SEARCH.equals(action));
  }
}
