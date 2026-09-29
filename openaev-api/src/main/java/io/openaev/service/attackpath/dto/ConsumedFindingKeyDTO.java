package io.openaev.service.attackpath.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.openaev.database.model.PrimitiveType;
import io.openaev.utils.SensitiveValueMaskingUtils;
import io.swagger.v3.oas.annotations.media.Schema;
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
 * <p>{@code matchedFindingIds} are the finding-node ids this key matched, resolved by the backend
 * (spec 011, back-authoritative). The front anchors the causal edge on these instead of
 * re-matching. Empty until resolved, or when nothing matched.
 *
 * <p>A condition targeting a password, a hash or a key carries the secret itself as its value, so
 * {@code value} is the <b>masked</b> form: it is what leaves the platform. Matching a key against a
 * finding needs the cleartext, so it is kept apart in {@code rawValue}, which is never serialized.
 * Splitting the two makes the DTO safe by construction - every factory and convenience constructor
 * masks, so no ordinary way of building it can put a secret on the wire - while keeping the matcher
 * exact. A record's canonical constructor cannot be narrowed below the visibility of the record
 * itself, so it stays reachable: it takes {@code value} and {@code rawValue} as two distinct
 * arguments though, which makes handing out a cleartext value a deliberate act rather than an
 * oversight.
 */
public record ConsumedFindingKeyDTO(
    String keyType,
    String operator,
    String value,
    String eventName,
    List<String> matchedFindingIds,
    @JsonIgnore @Schema(hidden = true) String rawValue) {

  /**
   * Built from a condition, masking the value when the key targets secret material; the matched
   * producing findings are resolved later (empty until then).
   */
  public static ConsumedFindingKeyDTO of(
      PrimitiveType keyType, String operator, String rawValue, String eventName) {
    return new ConsumedFindingKeyDTO(
        keyType == null ? null : keyType.label,
        operator,
        SensitiveValueMaskingUtils.maskIfNeeded(keyType, rawValue),
        eventName,
        List.of(),
        rawValue);
  }

  /**
   * Built from a key type <em>label</em>, for callers that only know the label. Resolves it back to
   * its {@link PrimitiveType} so the same masking applies as in {@link #of}: the invariant must
   * hold for every way of building the record, not only for the one production happens to use
   * today. An unknown or null label resolves to no type, hence to "not sensitive", so a label the
   * enum does not know can never fail the construction.
   */
  public ConsumedFindingKeyDTO(String keyType, String operator, String value, String eventName) {
    this(
        keyType,
        operator,
        SensitiveValueMaskingUtils.maskIfNeeded(
            PrimitiveType.fromLabelOptional(keyType).orElse(null), value),
        eventName,
        List.of(),
        value);
  }

  /** A copy carrying the finding-node ids this key matched. */
  public ConsumedFindingKeyDTO withMatchedFindingIds(List<String> ids) {
    return new ConsumedFindingKeyDTO(keyType, operator, value, eventName, ids, rawValue);
  }
}
