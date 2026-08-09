package io.openaev.service.attackpath.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;
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

  // INJECTOR, from the run snapshot: the injector's real type and the contract's ATT&CK techniques,
  // resolved once per graph, batched, rather than per node.
  private String injectorType;
  private List<AttackPathAttackPatternDTO> attackPatterns;

  // For an ASSET-typed node, what real entity it stands for: ENDPOINT (default, a machine), TEAM,
  // PERSON or ASSET_GROUP. All target kinds share the ASSET layout band, so the front keys off this
  // to pick the icon (and, for a TEAM, to read its persons as children). Null == ENDPOINT.
  private String entityKind;

  // ASSET (endpoint), from the run snapshot
  private String hostname;
  private String ip;
  // ASSET: the endpoint's seen (primary) IP, resolved live from the asset, so the map node shows a
  // single relevant IP rather than the frozen full IP list. Null when the asset has no seen IP.
  private String seenIp;
  private String platform;
  // ASSET: the endpoint's business criticality (VERY_HIGH..LOW / UNKNOWN), resolved from the asset,
  // so
  // the chokepoint score can weight "most findings" by "most critical". Null for discovered
  // endpoints
  // (no backing asset).
  private String criticality;
  private List<String> agents;
  // The raw endpoint key (asset id or discovered raw value); the ref the front passes to the
  // expand/relations reads to load an endpoint's detail on click.
  private String ref;
  // Collapsed mode only: distinct finding values per type on this endpoint (finding_type -> count).
  private Map<String, Long> findingCounts;

  // EXECUTION (left feed)
  private String payloadName;
  // EXECUTION: the human-readable name of the injector contract that was run (e.g. "NMAP SYN
  // Scan"),
  // resolved from the contract's labels, so the graph can name WHAT was launched on the
  // inject→endpoint
  // edge. Null when the execution carries no resolvable contract.
  private String contractName;
  private String executedAt;
  private String agentName;
  private String privilege;
  private String stepTemplateId;
  // EXECUTION: the run's inject and, when it has one, the payload it ran — resolved from the
  // durable
  // step the frozen row is keyed by, exactly as the Result drawer's detail read does. They let the
  // front address this execution's live inject without first fetching its detail row.
  private String injectId;
  private String payloadId;
  // EXECUTION: whether the inject actually RAN (EXECUTED / ERROR / PENDING…), as opposed to whether
  // it was caught, which is what `status` above carries. Shipped with the graph so a list of
  // executions renders it on first paint; the front previously needed two sequential fetches per
  // visible row (detail for the injectId, then the inject's status) and so showed nothing for a
  // second or two. Null when the row has no resolvable inject or its inject has no status yet.
  private String executionStatus;
  // Kill-chain, resolved per step template (keyed by stepTemplateId): the step templates this one
  // depends on, and the finding keys it consumes. Full mode only; the front correlates by
  // stepTemplateId to draw the causal edges.
  private List<String> dependsOn;
  private List<ConsumedFindingKeyDTO> consumedFindingKeys;
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
  // FINDING only: the per-finding verdict triple, worst-of aggregated across the producing
  // executions. Null on every other node type (omitted from the JSON).
  private AttackPathFindingVerdictsDTO verdicts;
  // FINDING only: false when the node is an output-only value (a chaining output not persisted as a
  // Finding, ADR-004), true for a real finding. Drives the "Output only" rendering and the degraded
  // drawer. Null on every other node type (omitted from the JSON).
  private Boolean isFinding;
}
