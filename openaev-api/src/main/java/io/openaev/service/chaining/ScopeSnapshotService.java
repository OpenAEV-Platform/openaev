package io.openaev.service.chaining;

import io.openaev.database.model.*;
import io.openaev.database.repository.CollectorRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.service.AssetGroupService;
import io.openaev.service.AssetService;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the immutable execution-time photos of a workflow's scope rules (see ADR-006). Composes
 * the existing resolution services ({@link AssetService}, {@link AssetGroupService}, {@link
 * CollectorRepository}) — it is an orchestrator, not a new resolver.
 *
 * <p>Two entry points, both operating on a RUN workflow's rules:
 *
 * <ul>
 *   <li>{@link #freezeLaunch(Workflow, String)} — sets the launch {@code snapshot} on each copied
 *       asset rule and appends one {@code SECURITY_PLATFORM} rule per connected tenant platform.
 *   <li>{@link #freezeEnd(Workflow)} — sets the {@code snapshotEnd} on every rule of a run reaching
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
    for (WorkflowScopeRule rule : workflowRun.getWorkflowScopeRules()) {
      rule.setSnapshotStart(buildSnapshot(rule));
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
  @Transactional
  public void freezeEnd(Workflow workflowRun) {
    for (WorkflowScopeRule rule : workflowRun.getWorkflowScopeRules()) {
      rule.setSnapshotEnd(buildEndSnapshot(rule));
    }
  }

  /**
   * Clears a provisionally frozen end photo. Used when a freshly launched autonomous run is
   * reopened (END back to RUN by {@code markSimulationWorkflowKeepAlive}): the launch evaluation
   * ended the empty run - freezing its end photo on the spot - before keep-alive parked it back in
   * RUN, and a live run must not carry an end reference or every later drift would be misclassified
   * as after-execution.
   *
   * @param workflowRun the reopened RUN workflow
   */
  @Transactional
  public void clearEnd(Workflow workflowRun) {
    for (WorkflowScopeRule rule : workflowRun.getWorkflowScopeRules()) {
      rule.setSnapshotEnd(null);
    }
  }

  // -- READ / DIFF --

  /**
   * Recomputes the change status of a rule from its two frozen snapshots and the current live state
   * (see ADR-006). Returns {@code null} for a rule without a launch snapshot (draft / scenario /
   * pre-ADR-006) — the frontend then falls back to live resolution.
   *
   * <p>The signature is <b>composition- and agent-aware</b> for an asset / group (label + each
   * frozen asset's id, name, agent count and executor set), so an asset added / removed / renamed
   * or an agent added / removed inside the scope flips the status — never a misleading "unchanged".
   * Precedence: a during-execution change dominates an after-execution one; while the run is still
   * RUNNING (no end snapshot) the current state is the in-progress end reference.
   */
  public ScopeRuleSnapshotStatus computeStatus(WorkflowScopeRule rule) {
    ScopeRuleSnapshot launch = rule.getSnapshotStart();
    if (launch == null) {
      return null;
    }
    ScopeRuleSnapshot end = rule.getSnapshotEnd();
    // Lifecycle (the run has ended) is carried by the photo's existence; a deletion at end time is
    // carried by its explicit `deleted` flag, which derives to a null end signature below.
    boolean ended = end != null;
    ScopeRuleSnapshot current = buildCurrentSnapshot(rule);
    return statusFrom(
        signature(launch), endSignature(end), current != null ? signature(current) : null, ended);
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
   * Change signature of a rule. Security platform: id + type + updatedAt (reinstall → current null
   * → DELETED; reconfiguration → later updatedAt → MODIFIED). Asset / group: label + the frozen
   * asset set (id, name, agent count, executor set) so a composition or agent change flips the
   * status. MANUAL / CSV: the raw label.
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
      Asset asset = assetService.asset(assetId);
      if (asset == null) {
        return null;
      }
      return ScopeRuleSnapshot.builder()
          .label(asset.getName())
          .assets(List.of(toAssetSnapshot(asset)))
          .build();
    } catch (ElementNotFoundException e) {
      // Only a genuine "no longer exists" maps to null (→ DELETED). Any other error must surface.
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
   * null (informative, not an allow/deny target — see ADR-006).
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
