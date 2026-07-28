package io.openaev.service.attackpath.ingestion;

import static io.openaev.utils.InjectContentUtils.MANUAL_TARGETS_CONTENT_KEY;
import static io.openaev.utils.InjectContentUtils.MANUAL_TARGET_SELECTOR;
import static io.openaev.utils.InjectContentUtils.TARGET_SELECTOR_CONTENT_KEY;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Agent;
import io.openaev.database.model.Asset;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Step;
import io.openaev.database.model.TechnicalInjectExpectation;
import io.openaev.database.repository.attackpath.AttackPathExecutionRepository;
import io.openaev.expectation.ExpectationType;
import io.openaev.service.AssetGroupService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Propagates expectation verdicts (prevention / detection / vulnerability) onto the attack-path
 * projection when a step's results land (#6647, spec 002, FR5). Until this existed the projection's
 * three status columns were written once at ingestion and never again, so a node's colour and
 * status label were frozen at "pending" for the life of the run — there was no verdict to stream.
 *
 * <p>One transaction per step event, not one per result: the caller hands over the step's whole
 * expectation set, the targets are resolved on the caller's already-loaded objects (no I/O inside
 * the transaction), and a single {@code executeNew} bumps the simulation's version once and applies
 * every update under it. As with the other two ingestion writers, the transaction is opened through
 * the tenant primitive rather than {@code @Transactional}, so the write carries the inject's tenant
 * and commits independently of the run; the caller recovers around this boundary, so a sync failure
 * can never fail the step.
 *
 * <p>Verdicts are grouped by (expectation type, status) before the transaction opens, so an
 * expectation on an asset group costs ONE update per type instead of one per member asset. The
 * grouping is also why the resolution work — including the asset-group expansion, memoized per sync
 * — happens entirely before the bump: the version row's lock is held until commit, and every
 * concurrent writer on the same simulation waits behind it.
 *
 * <p>The updates are guarded (see {@link AttackPathExecutionRepository}), so replaying an identical
 * result matches zero rows and changes nothing. The version bump happens before them, so a batch
 * that turns out to change nothing still costs clients one empty delta tick — deliberate: the
 * alternative is a pre-check read on every execution event, and an empty tick is one cheap indexed
 * poll.
 */
@Service
@RequiredArgsConstructor
public class AttackPathVerdictSyncService {

  private final TenantScopedTransaction tenantTx;
  private final AttackPathExecutionRepository executionRepository;
  private final AttackPathVersionService versionService;
  private final AssetGroupService assetGroupService;

  /** The granularity a verdict lands at, mirroring the output's endpoint-context priority order. */
  private enum Granularity {
    AGENT,
    ASSET,
    TARGET_KEY
  }

  /** One resolved verdict to write: an expectation type, its label, and the row key to match on. */
  private record Verdict(
      EXPECTATION_TYPE type, String status, Granularity granularity, String key) {}

  /** The rows one statement updates: a verdict's type and label, and the keys it applies to. */
  private record VerdictGroup(EXPECTATION_TYPE type, String status, Granularity granularity) {}

  /**
   * Syncs the step's expectation verdicts onto the simulation's execution rows. No-op for an inject
   * outside a simulation, and for a step whose expectations carry no resolvable verdict yet.
   *
   * <p>Precondition: must be called from inside an active tenant-scoped transaction. The write is
   * opened with {@code executeNew} so it commits independently of the run — the whole point of the
   * boundary — and that primitive refuses to run at the top level. On the chaining path the ambient
   * transaction comes from {@code StepEventService}, which opens one per update event with {@code
   * tenantTx.execute(TxCtx.forTenant(...))} before {@code InjectExecutionStep.update} reaches here.
   * Any other caller, tests included, has to open one the same way.
   */
  public void sync(Step stepRun, Inject inject, List<BaseInjectExpectation> expectations) {
    if (inject.getExercise() == null || stepRun == null || stepRun.getId() == null) {
      return; // the attack path is simulation-scoped, and the rows are keyed by the step
    }
    // Resolved outside the transaction: reading the expectation's agent/asset/asset-group can touch
    // lazy associations, and the write transaction must stay DB-only and short.
    Map<VerdictGroup, Set<String>> grouped = group(resolveVerdicts(inject, expectations));
    if (grouped.isEmpty()) {
      return; // nothing to write, so nothing to version
    }
    String simulationId = inject.getExercise().getId();
    String tenantId = inject.getTenant().getId();
    String stepId = stepRun.getId();
    tenantTx.executeNew(
        TxCtx.forTenant(tenantId),
        () -> {
          long version = versionService.bump(simulationId, tenantId);
          grouped.forEach((group, keys) -> apply(group, keys, stepId, tenantId, version));
        });
  }

  /**
   * The verdicts worth writing, from the step's expectations. Only technical expectations carry a
   * projection verdict (PREVENTION / DETECTION / VULNERABILITY); one verdict is derived per
   * expectation from its aggregate score, using the platform's own {@link ExpectationType#label},
   * so the projection never invents a label the rest of the platform would not show.
   *
   * <p>An expectation with no score yet is skipped rather than written as "pending": the projection
   * expresses pending as a null column, which is what the rows already hold.
   */
  private List<Verdict> resolveVerdicts(Inject inject, List<BaseInjectExpectation> expectations) {
    List<Verdict> verdicts = new ArrayList<>();
    // One expansion per asset group per sync, not per expectation: a step's expectations very often
    // name the same group, and each expansion is a resolution of its (possibly dynamic) members.
    Map<String, List<Asset>> membersByGroup = new HashMap<>();
    for (BaseInjectExpectation expectation : expectations) {
      if (!(expectation instanceof TechnicalInjectExpectation technical)) {
        continue;
      }
      EXPECTATION_TYPE type = technical.getType();
      if (type != EXPECTATION_TYPE.PREVENTION
          && type != EXPECTATION_TYPE.DETECTION
          && type != EXPECTATION_TYPE.VULNERABILITY) {
        continue;
      }
      if (technical.getScore() == null || technical.getExpectedScore() == null) {
        continue; // still pending: the projection's null column already says so
      }
      String status =
          ExpectationType.label(type, technical.getExpectedScore(), technical.getScore());
      addTargets(inject, technical, type, status, membersByGroup, verdicts);
    }
    return verdicts;
  }

  /**
   * The rows a verdict applies to, in the same priority order as the step output's endpoint
   * context: agent-level when the expectation names an agent, else the asset, else every member
   * asset of the named asset group (static and filter-matched alike).
   *
   * <p>An expectation that names none of the three is inject-level. Its rows are the ones the
   * ingestion wrote with {@code setTargetDiscoveredInformation}: they carry no asset id and no
   * agent, only the raw target key. Those keys are not on the expectation — they are the inject's
   * manual targets — so they are derived from the inject content exactly as {@link
   * AttackPathExecutionIngestionService} derived them when it wrote the rows. Anything else
   * inject-level carries no key to match a row by and is dropped rather than sprayed over the
   * step's rows.
   */
  private void addTargets(
      Inject inject,
      TechnicalInjectExpectation technical,
      EXPECTATION_TYPE type,
      String status,
      Map<String, List<Asset>> membersByGroup,
      List<Verdict> verdicts) {
    Agent agent = technical.getAgent();
    if (agent != null) {
      verdicts.add(new Verdict(type, status, Granularity.AGENT, agent.getId()));
      return;
    }
    Asset asset = technical.getAsset();
    if (asset != null) {
      verdicts.add(new Verdict(type, status, Granularity.ASSET, asset.getId()));
      return;
    }
    AssetGroup assetGroup = technical.getAssetGroup();
    if (assetGroup != null) {
      for (Asset member :
          membersByGroup.computeIfAbsent(
              assetGroup.getId(), id -> assetGroupService.assetsFromAssetGroup(assetGroup))) {
        verdicts.add(new Verdict(type, status, Granularity.ASSET, member.getId()));
      }
      return;
    }
    for (String targetKey : manualTargetKeys(inject)) {
      verdicts.add(new Verdict(type, status, Granularity.TARGET_KEY, targetKey));
    }
  }

  /**
   * The discovered target keys of an inject with a manual selector, split the same way the
   * ingestion split them, so a key derived here matches a row written there.
   */
  private List<String> manualTargetKeys(Inject inject) {
    ObjectNode content = inject.getContent();
    if (content == null) {
      return List.of();
    }
    JsonNode selector = content.get(TARGET_SELECTOR_CONTENT_KEY);
    JsonNode targets = content.get(MANUAL_TARGETS_CONTENT_KEY);
    if (selector == null
        || !MANUAL_TARGET_SELECTOR.equals(selector.asText())
        || targets == null
        || targets.isNull()) {
      return List.of();
    }
    // Split raw, without trimming: the ingestion did not trim either, and a key that differs by one
    // space matches no row.
    return List.of(targets.asText().split(","));
  }

  /**
   * Groups the verdicts into one statement per (type, status, granularity). Deduplicated keys: the
   * same asset can be reached by several expectations of one type, and updating it twice would be
   * two statements for one row.
   */
  private Map<VerdictGroup, Set<String>> group(List<Verdict> verdicts) {
    Map<VerdictGroup, Set<String>> grouped = new LinkedHashMap<>();
    for (Verdict verdict : verdicts) {
      grouped
          .computeIfAbsent(
              new VerdictGroup(verdict.type(), verdict.status(), verdict.granularity()),
              key -> new LinkedHashSet<>())
          .add(verdict.key());
    }
    return grouped;
  }

  private void apply(
      VerdictGroup group, Collection<String> keys, String stepId, String tenantId, long version) {
    switch (group.type()) {
      case PREVENTION ->
          applyPrevention(group.granularity(), keys, stepId, group.status(), tenantId, version);
      case DETECTION ->
          applyDetection(group.granularity(), keys, stepId, group.status(), tenantId, version);
      case VULNERABILITY ->
          applyVulnerability(group.granularity(), keys, stepId, group.status(), tenantId, version);
      default -> {
        // resolveVerdicts only emits the three technical types
      }
    }
  }

  private void applyPrevention(
      Granularity granularity,
      Collection<String> keys,
      String stepId,
      String status,
      String tenantId,
      long version) {
    switch (granularity) {
      case AGENT ->
          keys.forEach(
              key ->
                  executionRepository.updatePreventionStatusByStepIdAndAgentId(
                      stepId, key, status, tenantId, version));
      case ASSET ->
          executionRepository.updatePreventionStatusByStepIdAndTargetAssetIds(
              stepId, keys, status, tenantId, version);
      case TARGET_KEY ->
          keys.forEach(
              key ->
                  executionRepository.updatePreventionStatusByStepIdAndTargetKey(
                      stepId, key, status, tenantId, version));
    }
  }

  private void applyDetection(
      Granularity granularity,
      Collection<String> keys,
      String stepId,
      String status,
      String tenantId,
      long version) {
    switch (granularity) {
      case AGENT ->
          keys.forEach(
              key ->
                  executionRepository.updateDetectionStatusByStepIdAndAgentId(
                      stepId, key, status, tenantId, version));
      case ASSET ->
          executionRepository.updateDetectionStatusByStepIdAndTargetAssetIds(
              stepId, keys, status, tenantId, version);
      case TARGET_KEY ->
          keys.forEach(
              key ->
                  executionRepository.updateDetectionStatusByStepIdAndTargetKey(
                      stepId, key, status, tenantId, version));
    }
  }

  private void applyVulnerability(
      Granularity granularity,
      Collection<String> keys,
      String stepId,
      String status,
      String tenantId,
      long version) {
    switch (granularity) {
      case AGENT ->
          keys.forEach(
              key ->
                  executionRepository.updateVulnerabilityStatusByStepIdAndAgentId(
                      stepId, key, status, tenantId, version));
      case ASSET ->
          executionRepository.updateVulnerabilityStatusByStepIdAndTargetAssetIds(
              stepId, keys, status, tenantId, version);
      case TARGET_KEY ->
          keys.forEach(
              key ->
                  executionRepository.updateVulnerabilityStatusByStepIdAndTargetKey(
                      stepId, key, status, tenantId, version));
    }
  }
}
