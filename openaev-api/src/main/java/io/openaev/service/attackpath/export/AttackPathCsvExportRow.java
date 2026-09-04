package io.openaev.service.attackpath.export;

import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvBindByPosition;
import lombok.Data;

/**
 * One row of the unified Attack Chaining CSV export: every action executed against an endpoint,
 * flattened into a single file. Endpoints that are chokepoints are flagged via {@link #criticality}
 * (set to {@code "Chokepoint"}) so a reader immediately sees which rows are the riskiest, while
 * every other column — target asset, action, command, result, remediation, etc. — is populated the
 * same way for every row regardless of chokepoint status.
 */
@Data
public class AttackPathCsvExportRow {

  /** {@code "Chokepoint"} when the row's targeted asset is a chokepoint endpoint, {@code "-"} otherwise. */
  @CsvBindByName(column = "Criticality")
  @CsvBindByPosition(position = 0)
  private String criticality;

  /** The endpoint/asset this action targeted. */
  @CsvBindByName(column = "Targeted Asset")
  @CsvBindByPosition(position = 1)
  private String targetAsset;

  /** Populated only on trace rows: the chain's execution order for this hop. */
  @CsvBindByName(column = "Step Order")
  @CsvBindByPosition(position = 2)
  private String hopOrder;

  @CsvBindByName(column = "Action ID")
  @CsvBindByPosition(position = 3)
  private String actionId;

  @CsvBindByName(column = "Action Name")
  @CsvBindByPosition(position = 4)
  private String actionName;

  /** Populated only on trace rows. */
  @CsvBindByName(column = "Command/Payload")
  @CsvBindByPosition(position = 5)
  private String commandOrPayload;

  @CsvBindByName(column = "Result")
  @CsvBindByPosition(position = 6)
  private String result;

  /** Populated only on chokepoint rows: how many findings this specific action produced. */
  @CsvBindByName(column = "Findings Produced")
  @CsvBindByPosition(position = 7)
  private String findingsProduced;

  /** Populated only on chokepoint rows. */
  @CsvBindByName(column = "Risk Score")
  @CsvBindByPosition(position = 8)
  private String riskScore;

  /** Populated only on chokepoint rows. */
  @CsvBindByName(column = "Downstream Node Count")
  @CsvBindByPosition(position = 9)
  private String downstreamNodeCount;

  /** Populated only on chokepoint rows. */
  @CsvBindByName(column = "Downstream Nodes")
  @CsvBindByPosition(position = 10)
  private String downstreamNodes;

  /** Populated only on trace rows. */
  @CsvBindByName(column = "Terminal Output")
  @CsvBindByPosition(position = 11)
  private String terminalOutput;

  /** Populated only on trace rows. */
  @CsvBindByName(column = "Error Message")
  @CsvBindByPosition(position = 12)
  private String errorMessage;

  /** Populated only on trace rows. */
  @CsvBindByName(column = "Agent/Executor")
  @CsvBindByPosition(position = 13)
  private String agent;

  /**
   * Remediation/mitigation note for the action behind this row, when one exists (blank otherwise) —
   * present on both chokepoint and trace rows, the same source and shape either way.
   */
  @CsvBindByName(column = "Remediation Note")
  @CsvBindByPosition(position = 14)
  private String remediationNote;

  /** Populated only on trace rows. */
  @CsvBindByName(column = "Start Time")
  @CsvBindByPosition(position = 15)
  private String startTime;

  /** Populated only on trace rows. */
  @CsvBindByName(column = "End Time")
  @CsvBindByPosition(position = 16)
  private String endTime;

  /** Populated only on trace rows. */
  @CsvBindByName(column = "Duration (ms)")
  @CsvBindByPosition(position = 17)
  private String durationMs;
}
