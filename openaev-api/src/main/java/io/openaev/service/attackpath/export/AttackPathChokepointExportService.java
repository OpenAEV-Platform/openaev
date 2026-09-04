package io.openaev.service.attackpath.export;

import io.openaev.database.model.Exercise;
import io.openaev.database.model.attackpath.AttackPathExecution;
import io.openaev.database.model.attackpath.AttackPathExecutionRemediation;
import io.openaev.database.model.attackpath.projection.AttackPathEndpointGroupRow;
import io.openaev.database.model.attackpath.projection.AttackPathEndpointTypeCountRow;
import io.openaev.database.repository.AssetRepository;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.InjectStatusRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.StepRepository;
import io.openaev.database.repository.attackpath.AttackPathExecutionRemediationRepository;
import io.openaev.database.repository.attackpath.AttackPathExecutionRepository;
import io.openaev.database.repository.attackpath.AttackPathFindingRepository;
import io.openaev.utils.CsvExportUtils;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Builds the rows of the Attack Chaining "Chokepoint Report" CSV export: the ranked list of
 * chokepoint endpoints, ported server-side from the exact scoring formula the UI uses ({@code
 * SimulationAttackPath.tsx}'s {@code CRITICALITY_WEIGHT}), so the report matches what the Attack
 * Path screen highlights.
 *
 * <p>Two fields are best-effort approximations rather than dedicated data, called out here and in
 * the changelog: (1) "chain(s) it repeats in" correlates chokepoints across simulations by their
 * raw endpoint key, since no cross-chain correlation entity exists; (2) "downstream nodes" is a
 * breadth-first reachability count over the simulation's own agent-to-target edges (an asset that
 * was pivoted from), not a formally modelled dependency graph.
 */
@Service
@RequiredArgsConstructor
public class AttackPathChokepointExportService {

  /**
   * Mirrors the front-end's {@code CRITICALITY_WEIGHT} exactly (see {@code
   * SimulationAttackPath.tsx}): a VERY_HIGH asset counts 4x a LOW one; unset/unknown criticality
   * counts as LOW (weight 1), never zero, so it is still ranked.
   */
  private static final Map<String, Integer> CRITICALITY_WEIGHT =
      Map.of(
          "VERY_HIGH", 4,
          "HIGH", 3,
          "MEDIUM", 2,
          "LOW", 1,
          "UNKNOWN", 1);

  private final AttackPathExecutionRepository executionRepository;
  private final AttackPathFindingRepository findingRepository;
  private final AttackPathExecutionRemediationRepository remediationRepository;
  private final AssetRepository assetRepository;
  private final InjectorContractRepository injectorContractRepository;
  private final ExerciseRepository exerciseRepository;
  private final StepRepository stepRepository;
  private final InjectStatusRepository injectStatusRepository;

  public List<AttackPathChokepointCsvExportRow> exportRows(String simulationId) {
    List<AttackPathEndpointGroupRow> endpoints =
        executionRepository.findEndpointGroups(simulationId);
    if (endpoints.isEmpty()) {
      return List.of();
    }

    Map<String, Long> totalFindingsByEndpointKey = totalFindingsByEndpointKey(simulationId);
    Map<String, String> criticalityByAssetId = criticalityByAssetId(endpoints);
    List<AttackPathExecution> executions =
        executionRepository.findAllBySimulationIdOrderByExecutedAtAsc(simulationId);
    Map<String, List<AttackPathExecution>> executionsByTargetKey =
        executions.stream().collect(Collectors.groupingBy(AttackPathExecution::getTargetKey));
    Map<String, Set<String>> downstreamByTargetAssetId = buildAdjacency(executions);
    Map<String, String> contractNameByExternalId = resolveContractNames(executions);
    Map<String, String> hostnameByKey =
        endpoints.stream()
            .collect(
                Collectors.toMap(
                    AttackPathEndpointGroupRow::targetKey,
                    e -> firstNonBlank(e.targetHostname(), e.targetKey()),
                    (a, b) -> a));
    Map<String, String> resultByStepId = resolveExecutionStatusesByStepId(executions);
    Map<String, Long> findingsCountByExecutionId = findingsCountByExecutionId(executions);
    // Every credential secret known anywhere in the simulation (findings-based), combined with
    // pattern-based masking for secrets embedded directly in a command/output that never became a
    // structured finding — same approach as the Execution Trace export, applied here too so a
    // chokepoint's command/output never leaks a clear secret either.
    Set<String> knownSecrets = credentialSecrets(simulationId);
    // The action's position in the simulation's overall chronological order — the same numbering
    // as the Execution Trace's "Step Order", so a reader can cross-reference "this chokepoint
    // action happened at step 7" against the full trace.
    Map<String, Integer> stepOrderByExecutionId = new HashMap<>();
    for (int i = 0; i < executions.size(); i++) {
      stepOrderByExecutionId.put(executions.get(i).getId(), i + 1);
    }

    // One entry per chokepoint endpoint, each carrying every action executed on it — an endpoint
    // that had several actions run against it yields several CSV rows sharing the same endpoint
    // name, risk score and downstream columns.
    record ChokepointCandidate(AttackPathEndpointGroupRow endpoint, int score) {}
    List<ChokepointCandidate> candidates = new ArrayList<>();
    for (AttackPathEndpointGroupRow endpoint : endpoints) {
      long totalFindings = totalFindingsByEndpointKey.getOrDefault(endpoint.targetKey(), 0L);
      if (totalFindings == 0) {
        // Only endpoints that actually carry findings are chokepoint candidates: an endpoint with
        // no findings cannot be "the most findings on the most critical endpoint".
        continue;
      }
      String criticality =
          endpoint.targetAssetId() == null
              ? "UNKNOWN"
              : criticalityByAssetId.getOrDefault(endpoint.targetAssetId(), "UNKNOWN");
      int weight = CRITICALITY_WEIGHT.getOrDefault(criticality, 1);
      candidates.add(new ChokepointCandidate(endpoint, (int) (totalFindings * weight)));
    }
    // Highest risk first, so the report reads top-down like the UI's chokepoint card.
    candidates.sort(Comparator.comparingInt(ChokepointCandidate::score).reversed());

    List<AttackPathChokepointCsvExportRow> rows = new ArrayList<>();
    for (ChokepointCandidate candidate : candidates) {
      AttackPathEndpointGroupRow endpoint = candidate.endpoint();
      String endpointName =
          CsvExportUtils.valueOrDash(
              firstNonBlank(endpoint.targetHostname(), endpoint.targetKey()));
      String chains = CsvExportUtils.joinOrDash(chainNames(simulationId, endpoint.targetKey()));

      Set<String> downstreamKeys =
          endpoint.targetAssetId() == null
              ? Set.of()
              : downstreamByTargetAssetId.getOrDefault(endpoint.targetAssetId(), Set.of());
      int downstreamNodeCount = downstreamKeys.size();
      String downstreamNodes =
          CsvExportUtils.joinOrDash(
              downstreamKeys.stream()
                  .map(key -> hostnameByKey.getOrDefault(key, key))
                  .sorted()
                  .toList());

      List<AttackPathExecution> incoming =
          executionsByTargetKey.getOrDefault(endpoint.targetKey(), List.of());
      // One row per action actually executed on this endpoint (chronological order), not just a
      // single "representative" one: the reader needs to see everything that was run here.
      List<AttackPathExecution> actionsRun =
          incoming.stream()
              .filter(e -> e.getContractExternalId() != null)
              .sorted(Comparator.comparing(AttackPathExecution::getExecutedAt))
              .toList();
      if (actionsRun.isEmpty()) {
        actionsRun = incoming;
      }

      for (AttackPathExecution execution : actionsRun) {
        AttackPathChokepointCsvExportRow row = new AttackPathChokepointCsvExportRow();
        row.setEndpointName(endpointName);
        Integer stepOrder = stepOrderByExecutionId.get(execution.getId());
        row.setStepOrder(stepOrder == null ? "-" : String.valueOf(stepOrder));
        row.setActionId(CsvExportUtils.valueOrDash(execution.getContractExternalId()));
        row.setActionName(
            CsvExportUtils.valueOrDash(
                execution.getContractExternalId() == null
                    ? null
                    : contractNameByExternalId.get(execution.getContractExternalId())));
        row.setCommandOrPayload(
            CsvExportUtils.valueOrDash(
                CredentialMaskingUtils.maskAll(execution.getCommand(), knownSecrets)));
        row.setTerminalOutput(
            CsvExportUtils.valueOrDash(
                CredentialMaskingUtils.maskAll(execution.getTerminalOutput(), knownSecrets)));
        String actionResult =
            execution.getStepId() == null ? null : resultByStepId.get(execution.getStepId());
        if (actionResult == null) {
          actionResult =
              firstNonBlank(execution.getPreventionStatus(), execution.getDetectionStatus());
        }
        row.setActionResult(CsvExportUtils.valueOrDash(actionResult));
        row.setFindingsProduced(findingsCountByExecutionId.getOrDefault(execution.getId(), 0L));
        row.setRiskScore(candidate.score());
        row.setDownstreamNodeCount(downstreamNodeCount);
        row.setDownstreamNodes(downstreamNodes);
        row.setChains(chains);
        row.setRemediationNote(remediationNote(List.of(execution)));
        rows.add(row);
      }
    }

    return rows;
  }

  private static final String CATEGORY_CREDENTIALS = "credentials";

  /**
   * Every credential secret (the part after {@code username:}) discovered anywhere in the
   * simulation — same logic as {@link AttackPathExecutionExportService#credentialSecrets}, kept in
   * sync so both exports mask identically.
   */
  private Set<String> credentialSecrets(String simulationId) {
    Set<String> secrets = new HashSet<>();
    for (io.openaev.database.model.attackpath.projection.AttackPathFindingRow finding :
        findingRepository.findGraphRows(simulationId)) {
      if (!CATEGORY_CREDENTIALS.equalsIgnoreCase(finding.type())) {
        continue;
      }
      String value = finding.value();
      if (value == null) {
        continue;
      }
      int separator = value.indexOf(':');
      if (separator >= 0) {
        String secret = value.substring(separator + 1);
        if (!secret.isEmpty()) {
          secrets.add(secret);
        }
      }
    }
    return secrets;
  }

  /**
   * Per-execution finding count: how many findings this specific action produced, batched via
   * {@link AttackPathFindingRepository#findByExecutionId} rather than one query per execution.
   */
  private Map<String, Long> findingsCountByExecutionId(List<AttackPathExecution> executions) {
    Map<String, Long> counts = new HashMap<>();
    for (AttackPathExecution execution : executions) {
      long count = findingRepository.findByExecutionId(execution.getId()).size();
      if (count > 0) {
        counts.put(execution.getId(), count);
      }
    }
    return counts;
  }

  private Map<String, String> resolveExecutionStatusesByStepId(
      List<AttackPathExecution> executions) {
    Set<String> stepIds =
        executions.stream()
            .map(AttackPathExecution::getStepId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    if (stepIds.isEmpty()) {
      return Map.of();
    }
    Map<String, String> injectIdByStepId = new HashMap<>();
    for (Object[] row : stepRepository.findInjectIdsByStepIds(stepIds)) {
      if (row[0] instanceof String stepId && row[1] instanceof String injectId) {
        injectIdByStepId.put(stepId, injectId);
      }
    }
    Map<String, String> statusByInjectId = new HashMap<>();
    if (!injectIdByStepId.isEmpty()) {
      for (Object[] row :
          injectStatusRepository.findStatusNamesByInjectIds(
              new HashSet<>(injectIdByStepId.values()))) {
        if (row[0] instanceof String injectId && row[1] != null) {
          statusByInjectId.put(injectId, row[1].toString());
        }
      }
    }
    Map<String, String> resultByStepId = new HashMap<>();
    injectIdByStepId.forEach(
        (stepId, injectId) -> {
          String status = statusByInjectId.get(injectId);
          if (status != null) {
            resultByStepId.put(stepId, status);
          }
        });
    return resultByStepId;
  }

  private Map<String, Long> totalFindingsByEndpointKey(String simulationId) {
    List<AttackPathEndpointTypeCountRow> typeCounts =
        findingRepository.findEndpointTypeCounts(simulationId);
    Map<String, Long> totals = new HashMap<>();
    for (AttackPathEndpointTypeCountRow row : typeCounts) {
      totals.merge(row.endpointKey(), row.distinctValues(), Long::sum);
    }
    return totals;
  }

  private Map<String, String> criticalityByAssetId(List<AttackPathEndpointGroupRow> endpoints) {
    Set<String> assetIds =
        endpoints.stream()
            .map(AttackPathEndpointGroupRow::targetAssetId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    if (assetIds.isEmpty()) {
      return Map.of();
    }
    Map<String, String> criticalityByAssetId = new HashMap<>();
    for (Object[] row : assetRepository.findCriticalityNameAndSeenIpByIds(assetIds)) {
      String assetId = (String) row[0];
      Object criticality = row[1];
      if (criticality != null) {
        criticalityByAssetId.put(assetId, criticality.toString());
      }
    }
    return criticalityByAssetId;
  }

  /**
   * Breadth-first reachability from every asset that was itself the source of an agent-executed
   * hop: {@code sourceAssetId -> targetKey} edges (a compromised asset pivoting onward), which is
   * the same directional relationship the front-end's map renders as arrows.
   */
  private Map<String, Set<String>> buildAdjacency(List<AttackPathExecution> executions) {
    Map<String, Set<String>> directEdges = new HashMap<>();
    for (AttackPathExecution execution : executions) {
      if (!"AGENT".equals(execution.getSourceKind()) || execution.getSourceAssetId() == null) {
        continue;
      }
      directEdges
          .computeIfAbsent(execution.getSourceAssetId(), k -> new LinkedHashSet<>())
          .add(execution.getTargetKey());
    }
    Map<String, Set<String>> reachableByAssetId = new HashMap<>();
    for (String start : directEdges.keySet()) {
      Set<String> visited = new LinkedHashSet<>();
      ArrayDeque<String> queue = new ArrayDeque<>(directEdges.getOrDefault(start, Set.of()));
      while (!queue.isEmpty()) {
        String current = queue.poll();
        if (!visited.add(current)) {
          continue;
        }
        queue.addAll(directEdges.getOrDefault(current, Set.of()));
      }
      reachableByAssetId.put(start, visited);
    }
    return reachableByAssetId;
  }

  private List<String> chainNames(String simulationId, String endpointKey) {
    List<String> otherSimulationIds =
        findingRepository.findOtherSimulationIdsByEndpointKey(endpointKey, simulationId);
    List<String> names = new ArrayList<>();
    names.add(
        exerciseRepository.findById(simulationId).map(Exercise::getName).orElse(simulationId));
    for (String otherSimulationId : otherSimulationIds) {
      names.add(
          exerciseRepository
              .findById(otherSimulationId)
              .map(Exercise::getName)
              .orElse(otherSimulationId));
    }
    return names;
  }

  private String remediationNote(List<AttackPathExecution> incoming) {
    Set<String> stepIds =
        incoming.stream()
            .map(AttackPathExecution::getStepId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    if (stepIds.isEmpty()) {
      return "";
    }
    List<String> values = new ArrayList<>();
    for (String stepId : stepIds) {
      for (AttackPathExecutionRemediation remediation :
          remediationRepository.findByStepId(stepId)) {
        if (remediation.getValues() != null && !remediation.getValues().isBlank()) {
          values.add(remediation.getValues());
        }
      }
    }
    return values.isEmpty() ? "" : String.join(" | ", values);
  }

  private Map<String, String> resolveContractNames(List<AttackPathExecution> executions) {
    Set<String> externalIds =
        executions.stream()
            .map(AttackPathExecution::getContractExternalId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    if (externalIds.isEmpty()) {
      return Map.of();
    }
    var contracts = injectorContractRepository.findAllByIdOrExternalIdIn(externalIds);
    Map<String, String> nameByExternalId = new HashMap<>();
    for (String externalId : externalIds) {
      contracts.stream()
          .filter(c -> externalId.equals(c.getExternalId()) || externalId.equals(c.getId()))
          .findFirst()
          .map(c -> contractLabel(c.getLabels()))
          .filter(Objects::nonNull)
          .ifPresent(name -> nameByExternalId.put(externalId, name));
    }
    return nameByExternalId;
  }

  private static String contractLabel(Map<String, String> labels) {
    if (labels == null || labels.isEmpty()) {
      return null;
    }
    String en = labels.get("en");
    return en != null ? en : labels.values().iterator().next();
  }

  private static String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return null;
  }
}
