package io.openaev.service.attackpath.dto;

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
 */
public record ConsumedFindingKeyDTO(
    String keyType, String operator, String value, String eventName) {}
