package io.openaev.service.attackpath.export;

import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvBindByPosition;
import lombok.Data;

/**
 * One row of the Attack Chaining "Chokepoint Report" CSV export: one action executed on a
 * chokepoint endpoint — an asset node whose findings, weighted by its business criticality, make it
 * a single point of failure for the chain (see {@link AttackPathChokepointExportService}). A
 * chokepoint endpoint that had several actions run against it produces several rows, all sharing
 * the same {@link #endpointName}, {@link #riskScore} and downstream columns, so a reader can group
 * by endpoint and immediately see, for that single most-critical point, everything that was run and
 * whether it worked — the "why is this the riskiest endpoint" answer in one place.
 */
@Data
public class AttackPathChokepointCsvExportRow {

  @CsvBindByName(column = "Chokepoint Endpoint")
  @CsvBindByPosition(position = 0)
  private String endpointName;

  /** This action's position in the simulation's overall chronological execution order. */
  @CsvBindByName(column = "Step Order")
  @CsvBindByPosition(position = 1)
  private String stepOrder;

  @CsvBindByName(column = "Action ID")
  @CsvBindByPosition(position = 2)
  private String actionId;

  @CsvBindByName(column = "Action Name")
  @CsvBindByPosition(position = 3)
  private String actionName;

  @CsvBindByName(column = "Command/Payload")
  @CsvBindByPosition(position = 4)
  private String commandOrPayload;

  @CsvBindByName(column = "Action Result")
  @CsvBindByPosition(position = 5)
  private String actionResult;

  @CsvBindByName(column = "Terminal Output")
  @CsvBindByPosition(position = 6)
  private String terminalOutput;

  @CsvBindByName(column = "Findings Produced")
  @CsvBindByPosition(position = 7)
  private long findingsProduced;

  @CsvBindByName(column = "Risk Score")
  @CsvBindByPosition(position = 8)
  private int riskScore;

  @CsvBindByName(column = "Downstream Node Count")
  @CsvBindByPosition(position = 9)
  private int downstreamNodeCount;

  @CsvBindByName(column = "Downstream Nodes")
  @CsvBindByPosition(position = 10)
  private String downstreamNodes;

  @CsvBindByName(column = "Chain(s)")
  @CsvBindByPosition(position = 11)
  private String chains;

  @CsvBindByName(column = "Remediation Note")
  @CsvBindByPosition(position = 12)
  private String remediationNote;
}
