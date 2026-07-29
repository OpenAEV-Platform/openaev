package io.openaev.service.attackpath.dto;

import java.util.List;

/**
 * One finding key a kill-chain step consumes as input (issue 5048).
 *
 * <p>Derived from a step template's leaf filter condition: {@code keyType} is the {@link
 * io.openaev.database.model.PrimitiveType} label (e.g. {@code "port"}, {@code "share_name"}),
 * {@code operator} is the leaf {@link io.openaev.database.model.ConditionType} name (e.g. {@code
 * "EQ"}), {@code value} is the condition's target value. The front reconciles the key-type
 * vocabulary to the produced-finding vocabulary when it matches these against findings.
 *
 * <p>{@code eventName} is the name of the root filter condition (the event) this key belongs to, so
 * the front can tell the analyst which event the consuming action was triggered by (e.g. "SMB UP")
 * rather than only the raw key match. Null when the event has no name.
 *
 * <p>{@code matchedFindingIds} are the finding-node ids ({@code NODE_FINDING|type|value}) this key
 * matched, resolved by the backend (spec 011, back-authoritative). The front anchors the causal
 * edge on these instead of re-matching. Empty until resolved, or when nothing matched.
 */
public record ConsumedFindingKeyDTO(
    String keyType,
    String operator,
    String value,
    String eventName,
    List<String> matchedFindingIds) {

  /**
   * Built from a condition; the matched producing findings are resolved later (empty until then).
   */
  public ConsumedFindingKeyDTO(String keyType, String operator, String value, String eventName) {
    this(keyType, operator, value, eventName, List.of());
  }

  /** A copy carrying the finding-node ids this key matched. */
  public ConsumedFindingKeyDTO withMatchedFindingIds(List<String> ids) {
    return new ConsumedFindingKeyDTO(keyType, operator, value, eventName, ids);
  }
}
