package io.openaev.service.attackpath.ingestion;

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
import java.util.List;
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
    ASSET
  }

  /** One resolved verdict to write: an expectation type, its label, and the row key to match on. */
  private record Verdict(
      EXPECTATION_TYPE type, String status, Granularity granularity, String key) {}

  /**
   * Syncs the step's expectation verdicts onto the simulation's execution rows. No-op for an inject
   * outside a simulation, and for a step whose expectations carry no resolvable verdict yet.
   */
  public void sync(Step stepRun, Inject inject, List<BaseInjectExpectation> expectations) {
    if (inject.getExercise() == null || stepRun == null || stepRun.getId() == null) {
      return; // the attack path is simulation-scoped, and the rows are keyed by the step
    }
    // Resolved outside the transaction: reading the expectation's agent/asset/asset-group can touch
    // lazy associations, and the write transaction must stay DB-only and short.
    List<Verdict> verdicts = resolveVerdicts(expectations);
    if (verdicts.isEmpty()) {
      return; // nothing to write, so nothing to version
    }
    String simulationId = inject.getExercise().getId();
    String tenantId = inject.getTenant().getId();
    String stepId = stepRun.getId();
    tenantTx.executeNew(
        TxCtx.forTenant(tenantId),
        () -> {
          long version = versionService.bump(simulationId, tenantId);
          verdicts.forEach(verdict -> apply(verdict, stepId, tenantId, version));
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
  private List<Verdict> resolveVerdicts(List<BaseInjectExpectation> expectations) {
    List<Verdict> verdicts = new ArrayList<>();
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
      addTargets(technical, type, status, verdicts);
    }
    return verdicts;
  }

  /**
   * The rows a verdict applies to, in the same priority order as the step output's endpoint
   * context: agent-level when the expectation names an agent, else the asset, else every member
   * asset of the named asset group (static and filter-matched alike).
   *
   * <p>Scope, stated plainly: an expectation that names none of the three carries no key to match a
   * row by, so its verdict is dropped rather than sprayed over the step's rows. That is the same
   * fidelity limit the repository keys were designed around, and the reason a discovered (raw)
   * target only receives a verdict when the expectation resolves to its asset.
   */
  private void addTargets(
      TechnicalInjectExpectation technical,
      EXPECTATION_TYPE type,
      String status,
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
      for (Asset member : assetGroupService.assetsFromAssetGroup(assetGroup)) {
        verdicts.add(new Verdict(type, status, Granularity.ASSET, member.getId()));
      }
    }
  }

  private void apply(Verdict verdict, String stepId, String tenantId, long version) {
    switch (verdict.type()) {
      case PREVENTION -> {
        if (verdict.granularity() == Granularity.AGENT) {
          executionRepository.updatePreventionStatusByStepIdAndAgentId(
              stepId, verdict.key(), verdict.status(), tenantId, version);
        } else {
          executionRepository.updatePreventionStatusByStepIdAndTargetAssetId(
              stepId, verdict.key(), verdict.status(), tenantId, version);
        }
      }
      case DETECTION -> {
        if (verdict.granularity() == Granularity.AGENT) {
          executionRepository.updateDetectionStatusByStepIdAndAgentId(
              stepId, verdict.key(), verdict.status(), tenantId, version);
        } else {
          executionRepository.updateDetectionStatusByStepIdAndTargetAssetId(
              stepId, verdict.key(), verdict.status(), tenantId, version);
        }
      }
      case VULNERABILITY -> {
        if (verdict.granularity() == Granularity.AGENT) {
          executionRepository.updateVulnerabilityStatusByStepIdAndAgentId(
              stepId, verdict.key(), verdict.status(), tenantId, version);
        } else {
          executionRepository.updateVulnerabilityStatusByStepIdAndTargetAssetId(
              stepId, verdict.key(), verdict.status(), tenantId, version);
        }
      }
      default -> {
        // resolveVerdicts only emits the three technical types
      }
    }
  }
}
