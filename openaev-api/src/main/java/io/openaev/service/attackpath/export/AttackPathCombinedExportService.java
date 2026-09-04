package io.openaev.service.attackpath.export;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Builds the rows of the single, merged Attack Chaining CSV export: the Chokepoint Report and the
 * Execution Trace combined into one file, one row per action/endpoint, in a single chronological
 * "Step Order" sequence. A row is marked "Chokepoint" in the Criticality column whenever its
 * targeted asset is a chokepoint endpoint — a chokepoint IS the targeted asset (just a
 * higher-criticality one), so it must never be duplicated as a separate row alongside the trace
 * row for the same step/action; only chokepoint-only actions (that have no matching execution-trace
 * row for the same action/step) are appended in addition to the trace rows.
 */
@Service
@RequiredArgsConstructor
public class AttackPathCombinedExportService {

  private final AttackPathChokepointExportService chokepointExportService;
  private final AttackPathExecutionExportService executionExportService;

  public List<AttackPathCsvExportRow> exportRows(String simulationId) {
    List<AttackPathChokepointCsvExportRow> chokepoints =
        chokepointExportService.exportRows(simulationId);
    List<AttackPathExecutionTraceCsvExportRow> traces =
        executionExportService.exportRows(simulationId);

    // Every step already present in the trace is enriched with "Chokepoint" criticality (and the
    // chokepoint-specific reporting fields: findings, risk score, downstream nodes, remediation)
    // when it matches a chokepoint action, instead of being duplicated as its own row.
    Map<String, AttackPathChokepointCsvExportRow> chokepointByKey = new HashMap<>();
    for (AttackPathChokepointCsvExportRow chokepoint : chokepoints) {
      chokepointByKey.put(chokepoint.getStepOrder() + "|" + chokepoint.getActionId(), chokepoint);
    }

    List<AttackPathCsvExportRow> rows = new ArrayList<>();
    Set<String> coveredKeys = new HashSet<>();
    for (AttackPathExecutionTraceCsvExportRow trace : traces) {
      String key = trace.getHopOrder() + "|" + trace.getActionId();
      rows.add(toCombinedRow(trace, chokepointByKey.get(key)));
      coveredKeys.add(key);
    }
    // Chokepoint actions that have no corresponding execution-trace row (e.g. chokepoint-only
    // reporting data) are appended so no data is lost, still ordered by Step Order below.
    for (AttackPathChokepointCsvExportRow chokepoint : chokepoints) {
      String key = chokepoint.getStepOrder() + "|" + chokepoint.getActionId();
      if (!coveredKeys.contains(key)) {
        rows.add(toCombinedRow(chokepoint));
      }
    }

    rows.sort(Comparator.comparingInt(AttackPathCombinedExportService::parseStepOrder));
    return rows;
  }

  private static int parseStepOrder(AttackPathCsvExportRow row) {
    try {
      return Integer.parseInt(row.getHopOrder());
    } catch (NumberFormatException e) {
      return Integer.MAX_VALUE;
    }
  }

  private AttackPathCsvExportRow toCombinedRow(AttackPathChokepointCsvExportRow chokepoint) {
    AttackPathCsvExportRow row = new AttackPathCsvExportRow();
    row.setCriticality("Chokepoint");
    row.setTargetAsset(chokepoint.getEndpointName());
    row.setHopOrder(chokepoint.getStepOrder());
    row.setActionId(chokepoint.getActionId());
    row.setActionName(chokepoint.getActionName());
    row.setCommandOrPayload(chokepoint.getCommandOrPayload());
    row.setResult(chokepoint.getActionResult());
    row.setFindingsProduced(String.valueOf(chokepoint.getFindingsProduced()));
    row.setRiskScore(String.valueOf(chokepoint.getRiskScore()));
    row.setDownstreamNodeCount(String.valueOf(chokepoint.getDownstreamNodeCount()));
    row.setDownstreamNodes(chokepoint.getDownstreamNodes());
    row.setTerminalOutput(chokepoint.getTerminalOutput());
    row.setErrorMessage("-");
    row.setAgent("-");
    row.setRemediationNote(chokepoint.getRemediationNote());
    row.setStartTime("-");
    row.setEndTime("-");
    row.setDurationMs("-");
    return row;
  }

  private AttackPathCsvExportRow toCombinedRow(
      AttackPathExecutionTraceCsvExportRow trace, AttackPathChokepointCsvExportRow chokepoint) {
    AttackPathCsvExportRow row = new AttackPathCsvExportRow();
    boolean isChokepoint = chokepoint != null;
    row.setCriticality(isChokepoint ? "Chokepoint" : "-");
    row.setHopOrder(String.valueOf(trace.getHopOrder()));
    row.setActionId(trace.getActionId());
    row.setActionName(trace.getActionName());
    row.setTargetAsset(trace.getTargetAsset());
    row.setCommandOrPayload(trace.getCommandOrPayload());
    row.setResult(trace.getResult());
    row.setFindingsProduced(
        isChokepoint ? String.valueOf(chokepoint.getFindingsProduced()) : "-");
    row.setRiskScore(isChokepoint ? String.valueOf(chokepoint.getRiskScore()) : "-");
    row.setDownstreamNodeCount(
        isChokepoint ? String.valueOf(chokepoint.getDownstreamNodeCount()) : "-");
    row.setDownstreamNodes(isChokepoint ? chokepoint.getDownstreamNodes() : "-");
    row.setTerminalOutput(trace.getTerminalOutput());
    row.setErrorMessage(trace.getErrorMessage());
    row.setAgent(trace.getAgent());
    row.setRemediationNote(
        isChokepoint ? chokepoint.getRemediationNote() : trace.getRemediationNote());
    row.setStartTime(trace.getStartTime());
    row.setEndTime(trace.getEndTime());
    row.setDurationMs(trace.getDurationMs());
    return row;
  }
}
