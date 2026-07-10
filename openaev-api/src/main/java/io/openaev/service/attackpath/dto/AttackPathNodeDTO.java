package io.openaev.service.attackpath.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * A node of the attack-path graph (issue 6647), named after the design's {@code AttackPathNodeDTO}.
 * One flat shape projected by {@code type}: an {@code INJECTOR}/{@code ASSET}/{@code FINDING_TYPE}/
 * {@code FINDING} fills its own fields, and an {@code EXECUTION} is used for the left feed. Null
 * fields are omitted from the JSON. The rich execution fields (expectations, arguments, traces)
 * stay null in the POC and are loaded on drawer open (D3).
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AttackPathNodeDTO {

  private String id;
  private String type;
  private String label;
  private String status;

  // ASSET (endpoint), from the run snapshot
  private String hostname;
  private String ip;
  private String platform;
  private List<String> agents;

  // EXECUTION (left feed)
  private String payloadName;
  private String executedAt;
  private String agentName;
  private String privilege;
  private String stepTemplateId;
  private String command;
  // Kept for the production drawer's shape; stay null in the POC (D3).
  private List<Object> expectations;
  private List<Object> arguments;
  private List<Object> executionsTraces;
  private List<String> findingsNodeIds;

  // FINDING / FINDING_TYPE
  private String value;
  private String typeFindings;
  private String findingsTypeNodeId;
  private String assetNodeId;
}
