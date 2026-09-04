package io.openaev.service.attackpath.export;

import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvBindByPosition;
import lombok.Data;

/**
 * One row of the Attack Chaining "Execution Trace" CSV export — one node/hop of a chain's full
 * execution history (see {@link AttackPathExecutionExportService}).
 *
 * <p>Two fields are deliberately approximate, and this is called out here (and in the changelog)
 * rather than hidden: (1) {@code result} is the durable {@code ExecutionStatus} of the inject
 * behind the hop (EXECUTED/PARTIAL/ERROR/...), not a fixed success/fail/blocked/skipped enum — the
 * closest existing fixed vocabulary to what was requested; the three expectation-derived labels
 * (prevention/detection/vulnerability) are folded into {@code errorMessage} as free-text context
 * when the result is not a clean success. (2) There is no separate "end time" stored per hop, only
 * a single {@code executedAt} instant, so {@code endTime}/{@code durationMs} are always blank.
 */
@Data
public class AttackPathExecutionTraceCsvExportRow {

  @CsvBindByName(column = "Chain ID")
  @CsvBindByPosition(position = 0)
  private String chainId;

  @CsvBindByName(column = "Chain Name")
  @CsvBindByPosition(position = 1)
  private String chainName;

  @CsvBindByName(column = "Step Order")
  @CsvBindByPosition(position = 2)
  private int hopOrder;

  @CsvBindByName(column = "Action/TTP ID")
  @CsvBindByPosition(position = 3)
  private String actionId;

  @CsvBindByName(column = "Action/TTP Name")
  @CsvBindByPosition(position = 4)
  private String actionName;

  @CsvBindByName(column = "Target Asset")
  @CsvBindByPosition(position = 5)
  private String targetAsset;

  @CsvBindByName(column = "Command/Payload")
  @CsvBindByPosition(position = 6)
  private String commandOrPayload;

  @CsvBindByName(column = "Output Summary")
  @CsvBindByPosition(position = 7)
  private String outputSummary;

  /**
   * The full, untruncated terminal output (stdout/response) of the hop, so the CSV alone is enough
   * to inspect what actually came back — {@link #outputSummary} stays a short preview for quick
   * scanning, this column carries the complete text (still capped at {@link
   * AttackPathExecutionExportService#TERMINAL_OUTPUT_MAX_LENGTH} to keep the export bounded on
   * pathological logs, flagged as truncated when it is cut).
   */
  @CsvBindByName(column = "Terminal Output")
  @CsvBindByPosition(position = 8)
  private String terminalOutput;

  @CsvBindByName(column = "Result")
  @CsvBindByPosition(position = 9)
  private String result;

  @CsvBindByName(column = "Error Message")
  @CsvBindByPosition(position = 10)
  private String errorMessage;

  @CsvBindByName(column = "Agent/Executor")
  @CsvBindByPosition(position = 11)
  private String agent;

  /**
   * Remediation/mitigation note recorded for this hop's step, if the payload behind it has one
   * (blank when it does not) — the same source and shape as the Chokepoint Report's remediation
   * column ({@link AttackPathExecutionRemediation}), surfaced here too so a reader going hop by hop
   * through the full trace doesn't need to cross-reference the chokepoint export separately.
   */
  @CsvBindByName(column = "Remediation Note")
  @CsvBindByPosition(position = 12)
  private String remediationNote;

  @CsvBindByName(column = "Start Time")
  @CsvBindByPosition(position = 13)
  private String startTime;

  @CsvBindByName(column = "End Time")
  @CsvBindByPosition(position = 14)
  private String endTime;

  @CsvBindByName(column = "Duration (ms)")
  @CsvBindByPosition(position = 15)
  private String durationMs;
}
