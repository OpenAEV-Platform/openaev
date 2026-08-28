package io.openaev.service.attackpath.ingestion;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Asset;
import io.openaev.database.model.Finding;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Step;
import io.openaev.database.model.attackpath.AttackPathExecution;
import io.openaev.database.repository.FindingRepository;
import io.openaev.database.repository.attackpath.AttackPathExecutionRepository;
import io.openaev.service.attackpath.AttackPathIds;
import io.openaev.service.attackpath.ingestion.AttackPathFindingWriter.FindingRow;
import io.openaev.service.attackpath.ingestion.AttackPathFindingWriter.Link;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Copies an inject's findings into the attack-path snapshot ({@code attackpath_finding} + {@code
 * attackpath_execution_finding}) on each execution event of a chained run, attributed to the
 * execution rows already frozen for that run, so the attack-path view shows real findings that
 * survive later edits or deletes of the source findings.
 *
 * <p>The whole copy runs in a REQUIRES_NEW transaction scoped to the inject's tenant (as the
 * Phase-A ingestion does), so it commits independently and takes the tenant explicitly from the
 * inject. The caller recovers around this boundary and gates it on the attack-path feature; a
 * failure here is non-fatal to the run.
 */
@Service
@RequiredArgsConstructor
public class AttackPathFindingIngestionService {

  private final TenantScopedTransaction tenantTx;
  private final AttackPathExecutionRepository executionRepository;
  private final FindingRepository findingRepository;
  private final AttackPathFindingWriter findingWriter;
  private final AttackPathVersionService versionService;

  public void copyFindings(Inject inject, Step step) {
    if (inject.getExercise() == null) {
      return; // simulation-scoped: no simulation, nothing to copy
    }
    String simulationId = inject.getExercise().getId();
    String tenantId = inject.getTenant().getId();
    tenantTx.executeNew(
        TxCtx.forTenant(tenantId),
        () -> copy(inject.getId(), step.getId(), simulationId, tenantId));
  }

  /**
   * One output-only value produced by the chaining and NOT persisted as a {@link Finding} ({@code
   * contract_output_element_is_finding = false}, ADR-004). {@code type} is the contract output type
   * label, {@code field} the output element key (never blank, so the row carries the full natural
   * key its deterministic id is derived from).
   */
  public record OutputValue(String type, String field, String value) {}

  /**
   * Copies the run's output-only values onto the attack-path snapshot with {@code is_finding =
   * false}, so the map shows every output the chaining used, not only the ones persisted as
   * findings. Same tenant-scoped REQUIRES_NEW boundary, versioning and idempotent writes as {@link
   * #copyFindings}; a failure here is non-fatal to the run.
   */
  public void copyOutputs(Inject inject, Step step, List<OutputValue> outputs) {
    if (inject.getExercise() == null || outputs.isEmpty()) {
      return;
    }
    String simulationId = inject.getExercise().getId();
    String tenantId = inject.getTenant().getId();
    tenantTx.executeNew(
        TxCtx.forTenant(tenantId),
        () -> copyOutputsTx(step.getId(), simulationId, tenantId, outputs));
  }

  private void copyOutputsTx(
      String stepId, String simulationId, String tenantId, List<OutputValue> outputs) {
    List<AttackPathExecution> rows = executionRepository.findByStepIdAndTenantId(stepId, tenantId);
    if (rows.isEmpty()) {
      return; // no frozen endpoint to attribute an output to yet
    }
    // An output value carries no per-value asset, so attribute it only when the step targets a
    // single endpoint - otherwise there is no signal to pick the right one (as the finding copy's
    // no-match fallback does).
    long distinctEndpoints =
        rows.stream().map(AttackPathExecution::getTargetKey).distinct().count();
    if (distinctEndpoints != 1) {
      return;
    }

    List<FindingRow> findingRows = new ArrayList<>();
    List<Link> links = new ArrayList<>();
    Set<String> seenRows = new HashSet<>();
    Set<String> seenLinks = new HashSet<>();

    for (OutputValue out : outputs) {
      for (AttackPathExecution row : rows) {
        String endpointKey = row.getTargetKey();
        String endpointId = row.getTargetAssetId();
        String endpointRaw = endpointId != null ? null : endpointKey;
        String id =
            AttackPathIds.findingRow(
                simulationId, out.type(), out.field(), out.value(), endpointKey);
        if (seenRows.add(id)) {
          findingRows.add(
              new FindingRow(
                  id,
                  tenantId,
                  simulationId,
                  out.type(),
                  out.field(),
                  out.value(),
                  endpointId,
                  endpointRaw,
                  endpointKey,
                  false));
        }
        if (seenLinks.add(row.getId() + '\u0000' + id)) {
          links.add(new Link(row.getId(), id));
        }
      }
    }

    if (findingRows.isEmpty() && links.isEmpty()) {
      return;
    }
    long version = versionService.bump(simulationId, tenantId);
    findingWriter.insertFindings(findingRows, version);
    findingWriter.insertLinks(links);
    versionService.publishChanged(simulationId, tenantId, version);
  }

  private void copy(String injectId, String stepId, String simulationId, String tenantId) {
    List<AttackPathExecution> rows = executionRepository.findByStepIdAndTenantId(stepId, tenantId);
    if (rows.isEmpty()) {
      return; // no frozen endpoint to attribute a finding to yet
    }
    List<Finding> findings = findingRepository.findAllByInjectIdAndTenantId(injectId, tenantId);
    if (findings.isEmpty()) {
      return;
    }

    // Index the run's endpoints by target asset; keep every row for the no-match fallback.
    Map<String, List<AttackPathExecution>> rowsByAsset = new HashMap<>();
    for (AttackPathExecution row : rows) {
      if (row.getTargetAssetId() != null) {
        rowsByAsset.computeIfAbsent(row.getTargetAssetId(), key -> new ArrayList<>()).add(row);
      }
    }

    List<FindingRow> findingRows = new ArrayList<>();
    List<Link> links = new ArrayList<>();
    Set<String> seenRows = new HashSet<>();
    Set<String> seenLinks = new HashSet<>();

    for (Finding finding : findings) {
      String type = finding.getType().getLabel();
      String field = finding.getField();
      String value = finding.getValue();

      for (AttackPathExecution row : resolveTargets(finding, rowsByAsset, rows)) {
        String endpointKey = row.getTargetKey();
        String endpointId = row.getTargetAssetId();
        String endpointRaw = endpointId != null ? null : endpointKey;
        String id = AttackPathIds.findingRow(simulationId, type, field, value, endpointKey);
        if (seenRows.add(id)) {
          findingRows.add(
              new FindingRow(
                  id,
                  tenantId,
                  simulationId,
                  type,
                  field,
                  value,
                  endpointId,
                  endpointRaw,
                  endpointKey,
                  true));
        }
        if (seenLinks.add(row.getId() + '\u0000' + id)) {
          links.add(new Link(row.getId(), id));
        }
      }
    }

    if (findingRows.isEmpty() && links.isEmpty()) {
      return; // nothing to write, so nothing to version: never bump on an empty copy
    }
    // Every row is prepared above, before the bump: the counter's row lock is held until this
    // transaction commits, so the less work between the bump and the commit, the shorter concurrent
    // writers on the same simulation block. Bumping and stamping inside the transaction is what
    // keeps a client from holding a version whose rows it has not been sent (#6647, spec 002); a
    // re-copied finding is re-stamped, so a newly added link reaches the next delta.
    long version = versionService.bump(simulationId, tenantId);
    findingWriter.insertFindings(findingRows, version);
    findingWriter.insertLinks(links);
    // A re-copied finding is re-stamped and a new link is inserted, so this batch did change the
    // graph: worth a nudge (an empty batch returned above, before the bump).
    versionService.publishChanged(simulationId, tenantId, version);
  }

  /**
   * The endpoints to attribute a finding to: the finding's own assets that are real targets of this
   * run. When none match (a finding with no asset, or an asset that is not a run target, e.g. a
   * discovered raw target), fall back to the run's endpoint only if the step has a single one
   * (source == target, or several agents on one endpoint) - never source-agent, and never sprayed
   * across several endpoints, where there is no per-finding signal to pick the right one. A finding
   * that cannot be tied to a single endpoint is skipped rather than shown on endpoints it was not
   * found on (multi-target per-finding attribution is out of the MVP scope).
   */
  private List<AttackPathExecution> resolveTargets(
      Finding finding,
      Map<String, List<AttackPathExecution>> rowsByAsset,
      List<AttackPathExecution> allRows) {
    List<AttackPathExecution> matched = new ArrayList<>();
    for (Asset asset : finding.getAssets()) {
      List<AttackPathExecution> byAsset = rowsByAsset.get(asset.getId());
      if (byAsset != null) {
        matched.addAll(byAsset);
      }
    }
    if (!matched.isEmpty()) {
      return matched;
    }
    long distinctEndpoints =
        allRows.stream().map(AttackPathExecution::getTargetKey).distinct().count();
    return distinctEndpoints == 1 ? allRows : List.of();
  }
}
