package io.openaev.service.attackpath.export;

import io.openaev.database.model.Exercise;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.attackpath.AttackPathExecution;
import io.openaev.database.model.attackpath.AttackPathExecutionRemediation;
import io.openaev.database.model.attackpath.projection.AttackPathFindingRow;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.InjectStatusRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.StepRepository;
import io.openaev.database.repository.attackpath.AttackPathExecutionRemediationRepository;
import io.openaev.database.repository.attackpath.AttackPathExecutionRepository;
import io.openaev.database.repository.attackpath.AttackPathFindingRepository;
import io.openaev.utils.CsvExportUtils;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Builds the rows of the Attack Chaining "Execution Trace" CSV export: the full, chronological,
 * per-hop execution history of one chain (simulation), extending the single-inject execution trace
 * export to the whole chain (one CSV row per {@code AttackPathExecution}, in order).
 *
 * <p>Credential secrets are masked in the exported command and terminal output exactly like the
 * execution-detail drawer does ({@code AttackPathGraphService#executionDetail}): every credential
 * value discovered anywhere in the simulation is replaced with a fixed-length mask wherever it
 * appears in free text, so a clear secret never leaves the server in the CSV either.
 */
@Service
@RequiredArgsConstructor
public class AttackPathExecutionExportService {

  private static final String CATEGORY_CREDENTIALS = "credentials";

  /**
   * {@code terminalOutput} can be large (it is TOASTed and not read by the graph). The export
   * summarizes it rather than embedding the full log, per the general requirement that big exports
   * must not blow up; the first {@value #OUTPUT_SUMMARY_MAX_LENGTH} characters are kept and the row
   * is flagged as truncated so a reader knows to open the execution detail in the UI for the rest.
   */
  private static final int OUTPUT_SUMMARY_MAX_LENGTH = 2000;

  /**
   * Cap for the full {@code Terminal Output} column — deliberately larger than the summary column
   * (this is meant to be the "read the whole thing" column) but still bounded so one pathological
   * log entry can't blow up the whole CSV export.
   */
  static final int TERMINAL_OUTPUT_MAX_LENGTH = 20_000;

  private static final DateTimeFormatter TIMESTAMP_FORMAT =
      DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);

  private final AttackPathExecutionRepository executionRepository;
  private final ExerciseRepository exerciseRepository;
  private final InjectorContractRepository injectorContractRepository;
  private final StepRepository stepRepository;
  private final InjectStatusRepository injectStatusRepository;
  private final AttackPathExecutionRemediationRepository remediationRepository;
  private final AttackPathFindingRepository findingRepository;

  public List<AttackPathExecutionTraceCsvExportRow> exportRows(String simulationId) {
    List<AttackPathExecution> executions =
        executionRepository.findAllBySimulationIdOrderByExecutedAtAsc(simulationId);

    String chainName = exerciseRepository.findById(simulationId).map(Exercise::getName).orElse("-");

    Map<String, String> contractNameByExternalId = resolveContractNames(executions);
    Map<String, String> resultByStepId = resolveExecutionStatusesByStepId(executions);
    Set<String> secrets = credentialSecrets(simulationId);

    List<AttackPathExecutionTraceCsvExportRow> rows = new java.util.ArrayList<>();
    int order = 0;
    for (AttackPathExecution execution : executions) {
      order++;
      rows.add(
          toRow(
              execution,
              order,
              simulationId,
              chainName,
              contractNameByExternalId,
              resultByStepId,
              secrets));
    }
    return rows;
  }

  /**
   * Every credential secret (the part after {@code username:}) discovered anywhere in the
   * simulation, so masking is simulation-wide rather than per-endpoint — a hop's command/output can
   * reference a credential harvested elsewhere in the same chain.
   */
  private Set<String> credentialSecrets(String simulationId) {
    Set<String> secrets = new HashSet<>();
    // Case-insensitive match on the finding type: seeded/imported data has been observed with both
    // "Credentials" and "credentials" for the same category, and a clear secret must never survive
    // in the export because of a casing mismatch.
    for (AttackPathFindingRow finding : findingRepository.findGraphRows(simulationId)) {
      if (!CATEGORY_CREDENTIALS.equalsIgnoreCase(finding.type())) {
        continue;
      }
      String secret = credentialSecret(finding.value());
      if (secret != null && !secret.isEmpty()) {
        secrets.add(secret);
      }
    }
    return secrets;
  }

  /** The secret half of a {@code username:password} credential value (never shown in the clear). */
  private static String credentialSecret(String value) {
    if (value == null) {
      return null;
    }
    int separator = value.indexOf(':');
    return separator >= 0 ? value.substring(separator + 1) : null;
  }

  private AttackPathExecutionTraceCsvExportRow toRow(
      AttackPathExecution execution,
      int hopOrder,
      String simulationId,
      String chainName,
      Map<String, String> contractNameByExternalId,
      Map<String, String> resultByStepId,
      Set<String> secrets) {
    AttackPathExecutionTraceCsvExportRow row = new AttackPathExecutionTraceCsvExportRow();
    row.setChainId(simulationId);
    row.setChainName(CsvExportUtils.valueOrDash(chainName));
    row.setHopOrder(hopOrder);

    row.setActionId(CsvExportUtils.valueOrDash(execution.getContractExternalId()));
    row.setActionName(
        CsvExportUtils.valueOrDash(
            execution.getContractExternalId() == null
                ? null
                : contractNameByExternalId.get(execution.getContractExternalId())));

    String targetAsset =
        firstNonBlank(
            execution.getTargetHostname(), execution.getTargetRawValue(), execution.getTargetKey());
    row.setTargetAsset(CsvExportUtils.valueOrDash(targetAsset));

    String commandOrPayload =
        CredentialMaskingUtils.maskAll(
            firstNonBlank(execution.getCommand(), execution.getPayloadName()), secrets);
    row.setCommandOrPayload(CsvExportUtils.valueOrDash(commandOrPayload));
    row.setOutputSummary(
        summarizeOutput(CredentialMaskingUtils.maskAll(execution.getTerminalOutput(), secrets)));
    row.setTerminalOutput(
        fullOutput(CredentialMaskingUtils.maskAll(execution.getTerminalOutput(), secrets)));

    // Result: the durable "did it run" status if resolvable, else fall back to the
    // prevention/detection expectation labels (best-effort — see the row's class-level doc).
    String result =
        execution.getStepId() == null ? null : resultByStepId.get(execution.getStepId());
    if (result == null) {
      result = firstNonBlank(execution.getPreventionStatus(), execution.getDetectionStatus());
    }
    row.setResult(CsvExportUtils.valueOrDash(result));

    boolean looksLikeFailure =
        result != null
            && (result.equalsIgnoreCase("ERROR")
                || result.equalsIgnoreCase("PARTIAL")
                || "Failed".equalsIgnoreCase(execution.getPreventionStatus())
                || "Failed".equalsIgnoreCase(execution.getDetectionStatus()));
    row.setErrorMessage(
        looksLikeFailure
            ? CsvExportUtils.joinOrDash(
                java.util.stream.Stream.of(
                        execution.getPreventionStatus(),
                        execution.getDetectionStatus(),
                        execution.getVulnerabilityStatus())
                    .filter(Objects::nonNull)
                    .toList())
            : "-");

    String agent = firstNonBlank(execution.getAgentName(), execution.getSourceInjector());
    row.setAgent(
        CsvExportUtils.valueOrDash(
            execution.getAgentPrivilege() == null || agent == null
                ? agent
                : agent + " (" + execution.getAgentPrivilege() + ")"));

    row.setStartTime(
        execution.getExecutedAt() == null
            ? "-"
            : TIMESTAMP_FORMAT.format(execution.getExecutedAt()));
    row.setRemediationNote(remediationNote(execution.getStepId()));
    // No per-hop end time/duration exists on AttackPathExecution — see class-level doc.
    row.setEndTime("-");
    row.setDurationMs("-");

    return row;
  }

  private String remediationNote(String stepId) {
    if (stepId == null) {
      return "";
    }
    List<String> values = new java.util.ArrayList<>();
    for (AttackPathExecutionRemediation remediation : remediationRepository.findByStepId(stepId)) {
      if (remediation.getValues() != null && !remediation.getValues().isBlank()) {
        values.add(remediation.getValues());
      }
    }
    return values.isEmpty() ? "" : String.join(" | ", values);
  }

  private String summarizeOutput(String terminalOutput) {
    if (terminalOutput == null || terminalOutput.isBlank()) {
      return "-";
    }
    if (terminalOutput.length() <= OUTPUT_SUMMARY_MAX_LENGTH) {
      return terminalOutput;
    }
    return terminalOutput.substring(0, OUTPUT_SUMMARY_MAX_LENGTH)
        + "... [truncated, see execution detail in the UI for the full log]";
  }

  private String fullOutput(String terminalOutput) {
    if (terminalOutput == null || terminalOutput.isBlank()) {
      return "-";
    }
    if (terminalOutput.length() <= TERMINAL_OUTPUT_MAX_LENGTH) {
      return terminalOutput;
    }
    return terminalOutput.substring(0, TERMINAL_OUTPUT_MAX_LENGTH)
        + "... [truncated, see execution detail in the UI for the full log]";
  }

  private static String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return null;
  }

  /** Batched contract name resolution — one query for the whole trace, not one per row. */
  private Map<String, String> resolveContractNames(List<AttackPathExecution> executions) {
    Set<String> externalIds =
        executions.stream()
            .map(AttackPathExecution::getContractExternalId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    if (externalIds.isEmpty()) {
      return Map.of();
    }
    List<InjectorContract> contracts =
        injectorContractRepository.findAllByIdOrExternalIdIn(externalIds);
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

  /**
   * Batched "did it run" status resolution, the exact same two-query pattern as {@code
   * AttackPathGraphService#applyExecutionStatuses}: {@code stepId -> injectId -> status}, so a
   * trace of many hops costs two extra queries total, not one per hop.
   */
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
}
