package io.openaev.service.attackpath.dto;

/**
 * One finding key a kill-chain step consumes as input (issue 5048).
 *
 * <p>Derived from a step template's leaf filter condition: {@code keyType} is the {@link
 * io.openaev.database.model.PrimitiveType} label (e.g. {@code "port"}, {@code "share_name"}),
 * {@code operator} is the leaf {@link io.openaev.database.model.ConditionType} name (e.g. {@code
 * "EQ"}), {@code value} is the condition's target value. The front reconciles the key-type
 * vocabulary to the produced-finding vocabulary when it matches these against findings.
 */
public record ConsumedFindingKeyDTO(String keyType, String operator, String value) {}
