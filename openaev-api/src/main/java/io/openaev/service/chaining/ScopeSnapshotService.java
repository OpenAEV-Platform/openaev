package io.openaev.service.chaining;

import static io.openaev.service.chaining.WorkflowEndService.WORKFLOW_END_CAUSE_BY_DELETION;

import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.*;
import io.openaev.database.repository.CollectorRepository;
import io.openaev.database.repository.TeamRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.service.AssetGroupService;
import io.openaev.service.AssetService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the immutable execution-time photos of a workflow's scope rules (see ADR-006). Composes
 * the existing resolution services ({@link AssetService}, {@link AssetGroupService}, {@link
 * CollectorRepository}, {@link TeamRepository}, {@link UserRepository}) - it is an orchestrator,
 * not a new resolver.
 *
 * <p>Two entry points, both operating on a RUN workflow's rules:
 *
 * <ul>
 *   <li>{@link #freezeLaunch(Workflow, String)} - sets the launch {@code snapshot} on each copied
 *       asset rule and appends one {@code SECURITY_PLATFORM} rule per connected tenant platform.
 *   <li>{@link #freezeEnd(Workflow)} - sets the {@code snapshotEnd} on every rule of a run reaching
 *       END/STOP, re-running the same resolution.
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ScopeSnapshotService {

  private final AssetService assetService;
  private final AssetGroupService assetGroupService;
  private final CollectorRepository collectorRepository;
  private final TeamRepository teamRepository;
  private final UserRepository userRepository;
  private final TenantScopedTransaction tenantTx;

  /**
   * Freezes the launch snapshot on a RUN workflow's rules and appends the connected security
   * platform rows. Operates on the transient (not-yet-persisted) rule collection so the cascade
   * persists everything when the run is saved.
   *
   * @param workflowRun the RUN workflow being created
   * @param tenantId the owning simulation's tenant (connected platforms are resolved for it)
   */
  @Transactional
  public void freezeLaunch(Workflow workflowRun, String tenantId) {
    workflowRun.getWorkflowScopeRules().addAll(buildSecurityPlatformRules(workflowRun, tenantId));
    List<WorkflowScopeRule> rules = new ArrayList<>(workflowRun.getWorkflowScopeRules());
    List<ScopeRuleSnapshot> photos =
        atSystemClearance(tenantId, () -> rules.stream().map(this::buildSnapshot).toList());
    for (int i = 0; i < rules.size(); i++) {
      rules.get(i).setSnapshotStart(photos.get(i));
    }
  }

  /**
   * Freezes the end snapshot on every rule of a run reaching END/STOP, re-running the same
   * resolution as launch. Security platform rules are re-resolved from their frozen id. A target
   * that no longer resolves is recorded as an explicit {@code deleted} photo (last known label +
   * deletion flag) - never degraded to a raw-id label, which the status derivation would misread as
   * a rename ({@code MODIFIED_DURING_EXECUTION}) instead of a deletion.
   *
   * @param workflowRun the RUN workflow whose execution just ended
   */
  @Transactional(readOnly = true)
  public void freezeEnd(Workflow workflowRun, WorkflowEndService.WORKFLOW_END_CAUSE cause) {
    if (WORKFLOW_END_CAUSE_BY_DELETION.contains(cause)) return;
    List<WorkflowScopeRule> rules = new ArrayList<>(workflowRun.getWorkflowScopeRules());
    List<ScopeRuleSnapshot> photos =
        atSystemClearance(
            tenantIdOf(workflowRun), () -> rules.stream().map(this::buildEndSnapshot).toList());
    for (int i = 0; i < rules.size(); i++) {
      rules.get(i).setSnapshotEnd(photos.get(i));
    }
  }

  /**
   * Resolves the photos with every marking of the run's tenant, whoever triggers the freeze (the
   * launching user, the timeout job, the queue consumer or the user who stops the run). Under each
   * caller's own clearance the launch and end photos would differ, and the diff would read {@code
   * MODIFIED_DURING_EXECUTION} for everyone. Only plain photos leave the new transaction; what a
   * reader may see of them is decided when they are read. Without a tenant, the work runs in the
   * caller's own scope.
   */
  private <T> T atSystemClearance(String tenantId, Supplier<T> work) {
    if (tenantId == null || tenantId.isBlank()) {
      return work.get();
    }
    return tenantTx.executeNew(TxCtx.forTenant(tenantId), work);
  }

  private static String tenantIdOf(Workflow workflowRun) {
    Exercise simulation = workflowRun.getSimulation();
    return simulation == null || simulation.getTenant() == null
        ? null
        : simulation.getTenant().getId();
  }

  // -- READ / DIFF --

  /**
   * Recomputes the change status of a rule from its two frozen snapshots and the current live state
   * (see ADR-006). Returns {@code null} for a rule without a launch snapshot (draft / scenario /
   * pre-ADR-006) - the frontend then falls back to live resolution.
   *
   * <p>The signature is <b>composition- and agent-aware</b> for an asset / group (label + each
   * frozen asset's id, name, agent count and executor set), so an asset added / removed / renamed
   * or an agent added / removed inside the scope flips the status - never a misleading "unchanged".
   * Precedence: a during-execution change dominates an after-execution one; while the run is still
   * RUNNING (no end snapshot) the current state is the in-progress end reference.
   */
  public ScopeRuleSnapshotStatus computeStatus(WorkflowScopeRule rule) {
    return computeStatus(rule, null);
  }

  /**
   * Same as {@link #computeStatus(WorkflowScopeRule)} for a reader who may not see every asset: the
   * frozen photos hold every member of a group (they are taken at the system clearance), while the
   * live state is read under the reader's own clearance, so the assets the reader cannot see are
   * taken out of the photos before they are compared. Without it, a group with a hidden member
   * would read {@code MODIFIED_DURING_EXECUTION} for that reader only.
   *
   * @param hiddenAssetIds the ids of the assets that exist but the reader cannot see (see {@link
   *     #hiddenAssetIds}), or {@code null} / empty for a reader who sees them all
   */
  public ScopeRuleSnapshotStatus computeStatus(WorkflowScopeRule rule, Set<String> hiddenAssetIds) {
    ScopeRuleSnapshot launch = withoutHidden(rule.getSnapshotStart(), hiddenAssetIds);
    if (launch == null) {
      return null;
    }
    // Audience photos frozen before TEAM / PLAYER resolution existed carry the raw id as label.
    // Deriving a status from such a photo would misread the now-resolvable name as a mid-run
    // rename (MODIFIED_DURING_EXECUTION); treat the rule as un-snapshotted instead.
    if (isDegradedAudiencePhoto(rule, launch)) {
      return null;
    }
    ScopeRuleSnapshot end = withoutHidden(rule.getSnapshotEnd(), hiddenAssetIds);
    // Lifecycle (the run has ended) is carried by the photo's existence; a deletion at end time is
    // carried by its explicit `deleted` flag, which derives to a null end signature below.
    boolean ended = end != null;
    ScopeRuleSnapshot current = buildCurrentSnapshot(rule);
    return statusFrom(
        signature(launch), endSignature(end), current != null ? signature(current) : null, ended);
  }

  /**
   * The photo without the assets the reader cannot see. A copy: the frozen photo itself is never
   * touched. A {@code null} photo, nothing hidden or no asset in the photo: returned as is.
   *
   * @param hiddenAssetIds the ids of the assets that exist but the reader cannot see
   */
  public static ScopeRuleSnapshot withoutHidden(
      ScopeRuleSnapshot photo, Set<String> hiddenAssetIds) {
    if (photo == null
        || hiddenAssetIds == null
        || hiddenAssetIds.isEmpty()
        || photo.getAssets() == null) {
      return photo;
    }
    return ScopeRuleSnapshot.builder()
        .label(photo.getLabel())
        .assets(
            photo.getAssets().stream()
                .filter(asset -> !hiddenAssetIds.contains(asset.getId()))
                .toList())
        .securityPlatform(photo.getSecurityPlatform())
        .deleted(photo.getDeleted())
        .build();
  }

  /**
   * Among {@code assetIds}, the assets that exist but the reader cannot see. An asset that does not
   * exist at all is not one of them: its rule must keep showing as deleted, which is not a
   * restriction. The reader's own scope answers "visible"; only what that leaves out is looked up
   * again at the system clearance, so most reads open no extra transaction.
   *
   * @param tenantId the tenant of the workflow whose rules are read
   */
  public Set<String> hiddenAssetIds(String tenantId, Collection<String> assetIds) {
    Set<String> visible = assetService.visibleAssetIds(assetIds);
    Set<String> notVisible =
        assetIds.stream()
            .filter(java.util.Objects::nonNull)
            .filter(id -> !visible.contains(id))
            .collect(Collectors.toSet());
    if (notVisible.isEmpty()) {
      return Set.of();
    }
    return atSystemClearance(tenantId, () -> assetService.visibleAssetIds(notVisible));
  }

  /**
   * A launch photo whose label is the rule's raw id for an audience (TEAM / PLAYER) rule: frozen
   * before those sources were resolved to names. Both the status derivation and the output mapper
   * ignore such photos so the frontend falls back to live resolution.
   */
  public static boolean isDegradedAudiencePhoto(WorkflowScopeRule rule, ScopeRuleSnapshot photo) {
    return (rule.getRuleSource() == ScopeRuleSource.TEAM
            || rule.getRuleSource() == ScopeRuleSource.PLAYER)
        && photo.getLabel() != null
        && photo.getLabel().equals(rule.getRuleValue());
  }

  /**
   * Signature of the frozen end photo, or {@code null} when there is none yet (still RUNNING) or
   * when it carries the explicit deletion marker (target gone before the run ended - derives to
   * {@code DELETED_DURING_EXECUTION}, never a raw-id "rename").
   */
  private String endSignature(ScopeRuleSnapshot end) {
    if (end == null || Boolean.TRUE.equals(end.getDeleted())) {
      return null;
    }
    return signature(end);
  }

  /**
   * Generic three-point verdict on a rule signature ({@code null} = the referenced entity no longer
   * exists). Precedence: during-execution over after-execution over resolved.
   */
  private ScopeRuleSnapshotStatus statusFrom(
      String launch, String end, String current, boolean ended) {
    String endReference = ended ? end : current;
    if (endReference == null) {
      return ScopeRuleSnapshotStatus.DELETED_DURING_EXECUTION;
    }
    if (!launch.equals(endReference)) {
      return ScopeRuleSnapshotStatus.MODIFIED_DURING_EXECUTION;
    }
    if (ended) {
      if (current == null) {
        return ScopeRuleSnapshotStatus.DELETED_AFTER_EXECUTION;
      }
      if (!end.equals(current)) {
        return ScopeRuleSnapshotStatus.MODIFIED_AFTER_EXECUTION;
      }
    }
    return ScopeRuleSnapshotStatus.RESOLVED;
  }

  /**
   * Change signature of a rule. Security platform: id + type + updatedAt (reinstall -> current null
   * -> DELETED; reconfiguration -> later updatedAt -> MODIFIED). Asset / group: label + the frozen
   * asset set (id, name, agent count, executor set) so a composition or agent change flips the
   * status. TEAM / PLAYER: the resolved name label (a rename flips the status, membership does
   * not). MANUAL / CSV: the raw label.
   */
  private String signature(ScopeRuleSnapshot snapshot) {
    if (snapshot.getSecurityPlatform() != null) {
      ScopeRuleSnapshot.SecurityPlatformSnapshot sp = snapshot.getSecurityPlatform();
      return sp.getId() + "|" + sp.getType() + "|" + sp.getUpdatedAt();
    }
    if (snapshot.getAssets() != null && !snapshot.getAssets().isEmpty()) {
      String composition =
          snapshot.getAssets().stream()
              .map(this::assetSignature)
              .sorted()
              .collect(Collectors.joining(","));
      return snapshot.getLabel() + "|" + composition;
    }
    return snapshot.getLabel();
  }

  private String assetSignature(ScopeRuleSnapshot.AssetSnapshot asset) {
    String executors =
        asset.getExecutors() == null
            ? ""
            : asset.getExecutors().stream().sorted().collect(Collectors.joining("+"));
    return asset.getId() + ":" + asset.getName() + ":" + asset.getAgentsCount() + ":" + executors;
  }

  // -- SNAPSHOT BUILDING --

  /**
   * Builds the current live snapshot of a rule, or {@code null} when the referenced entity no
   * longer exists (deletion / reinstall). Used at read time for the diff.
   *
   * <p>Deliberately NOT {@code @Transactional}: it is self-invoked by {@link #computeStatus} and
   * the freeze paths (an intra-class call would bypass the Spring proxy anyway), and every caller
   * already runs inside a transaction (the freeze paths) or a read-scoped request (the mapper).
   */
  public ScopeRuleSnapshot buildCurrentSnapshot(WorkflowScopeRule rule) {
    return switch (rule.getRuleSource()) {
      case ASSET -> resolveAssetSnapshot(rule.getRuleValue());
      case ASSET_GROUP -> resolveAssetGroupSnapshot(rule.getRuleValue());
      case SECURITY_PLATFORM -> resolveSecurityPlatformSnapshot(rule.getRuleValue());
      // Audience rules resolve to human-readable labels (team name / player name-or-email) so a
      // launched simulation's scope never displays a raw id, and a rename / deletion mid-run is
      // derivable exactly like an asset's.
      case TEAM -> resolveTeamSnapshot(rule.getRuleValue());
      case PLAYER -> resolvePlayerSnapshot(rule.getRuleValue());
      // MANUAL / CSV: the value is the label itself and can never be "deleted".
      case MANUAL, CSV -> ScopeRuleSnapshot.builder().label(rule.getRuleValue()).build();
      default -> ScopeRuleSnapshot.builder().label(rule.getRuleValue()).build();
    };
  }

  /**
   * Launch-time build: like {@link #buildCurrentSnapshot} but degrades a missing entity to its raw
   * id (workaround) instead of null, so the launch photo always carries at least a label.
   */
  private ScopeRuleSnapshot buildSnapshot(WorkflowScopeRule rule) {
    ScopeRuleSnapshot resolved = buildCurrentSnapshot(rule);
    return resolved != null
        ? resolved
        : ScopeRuleSnapshot.builder().label(rule.getRuleValue()).build();
  }

  /**
   * End-time build: a target that no longer resolves yields an explicit {@code deleted} photo
   * carrying the last known (launch) label, so the end reference exists (lifecycle: the run HAS
   * ended) while the deletion stays derivable as {@code DELETED_DURING_EXECUTION}.
   */
  private ScopeRuleSnapshot buildEndSnapshot(WorkflowScopeRule rule) {
    ScopeRuleSnapshot resolved = buildCurrentSnapshot(rule);
    if (resolved != null) {
      return resolved;
    }
    String lastKnownLabel =
        rule.getSnapshotStart() != null && rule.getSnapshotStart().getLabel() != null
            ? rule.getSnapshotStart().getLabel()
            : rule.getRuleValue();
    return ScopeRuleSnapshot.builder().label(lastKnownLabel).deleted(true).build();
  }

  private ScopeRuleSnapshot resolveAssetSnapshot(String assetId) {
    try {
      // Reads under the caller's own scope, exactly like resolveSecurityPlatformSnapshot below.
      // This used to open a REQUIRES_NEW transaction scoped to TenantContext.getCurrentTenant(),
      // which falls back to the DEFAULT tenant: on the timeout job, which sets no thread-local,
      // every asset of another tenant failed to resolve and its rule was frozen as
      // DELETED_DURING_EXECUTION although the asset still existed. Scoping is the caller's job, and
      // both freeze paths now establish it.
      Asset asset = assetService.asset(assetId);
      if (asset == null) {
        return null;
      }
      return ScopeRuleSnapshot.builder()
          .label(asset.getName())
          .assets(List.of(toAssetSnapshot(asset)))
          .build();
    } catch (ElementNotFoundException e) {
      // Only a genuine "no longer exists" maps to null (-> DELETED). Any other error must surface.
      return null;
    }
  }

  private ScopeRuleSnapshot resolveAssetGroupSnapshot(String assetGroupId) {
    try {
      AssetGroup group = assetGroupService.assetGroup(assetGroupId);
      if (group == null) {
        return null;
      }
      List<ScopeRuleSnapshot.AssetSnapshot> assets =
          assetGroupService.assetsFromAssetGroup(group).stream()
              .map(this::toAssetSnapshot)
              .toList();
      return ScopeRuleSnapshot.builder().label(group.getName()).assets(assets).build();
    } catch (ElementNotFoundException e) {
      return null;
    }
  }

  private ScopeRuleSnapshot resolveTeamSnapshot(String teamId) {
    return teamRepository
        .findByIdAndTenantId(teamId, TenantContext.getCurrentTenant())
        .map(team -> ScopeRuleSnapshot.builder().label(team.getName()).build())
        .orElse(null);
  }

  private ScopeRuleSnapshot resolvePlayerSnapshot(String userId) {
    return userRepository
        .findAllByIdInAndTenantId(List.of(userId), TenantContext.getCurrentTenant())
        .stream()
        .findFirst()
        .map(user -> ScopeRuleSnapshot.builder().label(user.getNameOrEmail()).build())
        .orElse(null);
  }

  private ScopeRuleSnapshot resolveSecurityPlatformSnapshot(String securityPlatformId) {
    try {
      Asset asset = assetService.asset(securityPlatformId);
      if (asset == null) {
        return null;
      }
      return toSecurityPlatformSnapshot((SecurityPlatform) Hibernate.unproxy(asset));
    } catch (ElementNotFoundException e) {
      return null;
    }
  }

  private ScopeRuleSnapshot.AssetSnapshot toAssetSnapshot(Asset asset) {
    List<String> executors = new ArrayList<>();
    int agentsCount = 0;
    if (Hibernate.unproxy(asset) instanceof Endpoint endpoint) {
      agentsCount = endpoint.getAgents().size();
      executors =
          endpoint.getAgents().stream()
              .map(Agent::getExecutor)
              .filter(java.util.Objects::nonNull)
              .map(Executor::getType)
              .distinct()
              .collect(Collectors.toCollection(ArrayList::new));
    }
    return ScopeRuleSnapshot.AssetSnapshot.builder()
        .id(asset.getId())
        .name(asset.getName())
        .agentsCount(agentsCount)
        .executors(executors)
        .build();
  }

  private ScopeRuleSnapshot toSecurityPlatformSnapshot(SecurityPlatform platform) {
    return ScopeRuleSnapshot.builder()
        .label(platform.getName())
        .securityPlatform(
            ScopeRuleSnapshot.SecurityPlatformSnapshot.builder()
                .id(platform.getId())
                .type(
                    platform.getSecurityPlatformType() != null
                        ? platform.getSecurityPlatformType().name()
                        : null)
                .updatedAt(platform.getUpdatedAt())
                .build())
        .build();
  }

  // -- SECURITY PLATFORM ROWS --

  /**
   * One {@code SECURITY_PLATFORM} rule per connected tenant security platform (a collector with a
   * non-null security platform FK), each carrying its launch snapshot. {@code selectedMode} is left
   * null (informative, not an allow/deny target - see ADR-006).
   */
  private List<WorkflowScopeRule> buildSecurityPlatformRules(
      Workflow workflowRun, String tenantId) {
    return collectorRepository.findAllByTenantIdAndSecurityPlatformIsNotNull(tenantId).stream()
        .map(Collector::getSecurityPlatform)
        .filter(java.util.Objects::nonNull)
        .distinct()
        .map(platform -> toSecurityPlatformRule(workflowRun, platform))
        .toList();
  }

  private WorkflowScopeRule toSecurityPlatformRule(
      Workflow workflowRun, SecurityPlatform platform) {
    return WorkflowScopeRule.builder()
        .ruleSource(ScopeRuleSource.SECURITY_PLATFORM)
        .valueType(ScopeRuleValueType.SECURITY_PLATFORM_ID)
        .ruleValue(platform.getId())
        .workflow(workflowRun)
        .build();
  }
}
