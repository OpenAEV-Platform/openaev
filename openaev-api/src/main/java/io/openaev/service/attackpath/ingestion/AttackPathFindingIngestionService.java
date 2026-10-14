package io.openaev.service.attackpath.ingestion;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Asset;
import io.openaev.database.model.Finding;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Step;
import io.openaev.database.model.Team;
import io.openaev.database.model.User;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
@Slf4j
@Service
@RequiredArgsConstructor
public class AttackPathFindingIngestionService {

  private static final String TARGET_KIND_PERSON = "PERSON";
  private static final String TARGET_KIND_TEAM = "TEAM";

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
   * Same copy as {@link #copyFindings}, deferred until the caller's transaction commits, for a
   * finding created outside the step's execution events: credentials a phishing recipient submits
   * long after the step ran raise no workflow update, so no later {@code
   * InjectExecutionStep#update} would copy them, and a copy run inline would not see the
   * still-uncommitted finding from its own REQUIRES_NEW transaction. The ids are read now, while
   * the caller's session is open. Non-fatal: a failure is logged and never reaches the caller.
   */
  public void copyFindingsAfterCommit(Inject inject, Step step) {
    if (inject.getExercise() == null) {
      return; // simulation-scoped: no simulation, nothing to copy
    }
    String injectId = inject.getId();
    String stepId = step.getId();
    String simulationId = inject.getExercise().getId();
    String tenantId = inject.getTenant().getId();
    Runnable deferredCopy =
        () -> {
          try {
            // Still flagged active in afterCommit: the copy nests as REQUIRES_NEW, as above.
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
              tenantTx.executeNew(
                  TxCtx.forTenant(tenantId), () -> copy(injectId, stepId, simulationId, tenantId));
            } else {
              tenantTx.execute(
                  TxCtx.forTenant(tenantId), () -> copy(injectId, stepId, simulationId, tenantId));
            }
          } catch (Exception e) {
            log.warn("Attack-path findings copy skipped for inject {} (non-fatal)", injectId, e);
          }
        };
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              deferredCopy.run();
            }
          });
    } else {
      deferredCopy.run();
    }
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

    // Index the run's endpoints by target asset, and a human-in-the-loop step's recipients and
    // teams by their target key (user / team id); keep every row for the no-match fallback.
    Map<String, List<AttackPathExecution>> rowsByAsset = new HashMap<>();
    Map<String, List<AttackPathExecution>> rowsByPerson = new HashMap<>();
    Map<String, List<AttackPathExecution>> rowsByTeam = new HashMap<>();
    for (AttackPathExecution row : rows) {
      if (row.getTargetAssetId() != null) {
        rowsByAsset.computeIfAbsent(row.getTargetAssetId(), key -> new ArrayList<>()).add(row);
      } else if (TARGET_KIND_PERSON.equals(row.getTargetKind())) {
        rowsByPerson.computeIfAbsent(row.getTargetKey(), key -> new ArrayList<>()).add(row);
      } else if (TARGET_KIND_TEAM.equals(row.getTargetKind())) {
        rowsByTeam.computeIfAbsent(row.getTargetKey(), key -> new ArrayList<>()).add(row);
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

      List<AttackPathExecution> targets =
          new ArrayList<>(resolveTargets(finding, rowsByAsset, rowsByPerson, rowsByTeam, rows));
      // A recipient's finding is its team's too: the PERSON row hangs off its team's row (source =
      // the team), the injector -> team row is the action's own execution, and every per-node read
      // keys on the finding's endpoint. So the team gets its own row, linked to its own execution,
      // or the team node, its panel and the action panel would all read empty.
      for (AttackPathExecution row : List.copyOf(targets)) {
        if (TARGET_KIND_PERSON.equals(row.getTargetKind())) {
          targets.addAll(rowsByTeam.getOrDefault(row.getSourceAssetId(), List.of()));
        }
      }

      for (AttackPathExecution row : targets) {
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
   * run. When none match, a human-in-the-loop finding (e.g. credentials a phishing recipient
   * submitted) carries no asset but names its users and teams instead: attribute it to the PERSON
   * rows of its users, or failing that to the TEAM rows of its teams - those rows are the run's
   * targets for a team-targeted inject, and a team step always spans several of them (the team and
   * its recipients), so the single-endpoint fallback below would drop the finding. Otherwise (a
   * finding with no asset, or an asset that is not a run target, e.g. a discovered raw target),
   * fall back to the run's endpoint only if the step has a single one (source == target, or several
   * agents on one endpoint) - never source-agent, and never sprayed across several endpoints, where
   * there is no per-finding signal to pick the right one. A finding that cannot be tied to a single
   * endpoint is skipped rather than shown on endpoints it was not found on (multi-target
   * per-finding attribution is out of the MVP scope).
   *
   * <p>{@code getUsers()} / {@code getTeams()} are lazy and read only when no asset matched; the
   * copy runs inside its own transaction, and the platform batch fetch size groups those loads
   * across the inject's findings (two bags cannot be fetch-joined alongside {@code assets}).
   */
  private List<AttackPathExecution> resolveTargets(
      Finding finding,
      Map<String, List<AttackPathExecution>> rowsByAsset,
      Map<String, List<AttackPathExecution>> rowsByPerson,
      Map<String, List<AttackPathExecution>> rowsByTeam,
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
    if (!rowsByPerson.isEmpty()) {
      for (User user : finding.getUsers()) {
        List<AttackPathExecution> byPerson = rowsByPerson.get(user.getId());
        if (byPerson != null) {
          matched.addAll(byPerson);
        }
      }
      if (!matched.isEmpty()) {
        return matched;
      }
    }
    if (!rowsByTeam.isEmpty()) {
      for (Team team : finding.getTeams()) {
        List<AttackPathExecution> byTeam = rowsByTeam.get(team.getId());
        if (byTeam != null) {
          matched.addAll(byTeam);
        }
      }
      if (!matched.isEmpty()) {
        return matched;
      }
    }
    long distinctEndpoints =
        allRows.stream().map(AttackPathExecution::getTargetKey).distinct().count();
    return distinctEndpoints == 1 ? allRows : List.of();
  }
}
