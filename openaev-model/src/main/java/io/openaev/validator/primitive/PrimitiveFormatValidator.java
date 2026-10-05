package io.openaev.validator.primitive;

import io.openaev.database.model.ConditionType;
import io.openaev.database.model.PrimitiveType;
import io.openaev.validator.primitive.PrimitiveTypePolicy.FormatEnforcement;
import io.openaev.validator.primitive.PrimitiveTypePolicy.TypePolicy;
import java.util.Set;

/**
 * Pure, stateless, operator-aware format validation of a primitive value.
 *
 * <p>This class holds the intrinsic syntax of a value and nothing else. It never looks at the
 * workflow scope allow/deny lists, never queries the database and never depends on a request
 * context - which is precisely what allows {@link PrimitiveTypePolicy} to be serialized as API
 * metadata and replayed by the frontend. Contextual acceptance is composed on top of it by {@code
 * PrimitiveValueValidator}.
 *
 * <h2>Why validation is operator-aware</h2>
 *
 * The expected value does not have the same nature depending on the operator, so the format of the
 * primitive type cannot be applied unconditionally:
 *
 * <ul>
 *   <li>{@code IS_NULL} / {@code IS_NOT_NULL} carry no value at all;
 *   <li>{@code IN} / {@code NIN} are evaluated as a comma-separated list of <b>substring</b>
 *       matches (see {@code ConditionUtils#evaluateLeafCondition}), so a partial value such as
 *       {@code 10.0.} is a legitimate input and a strict rule would break it;
 *   <li>{@code GT} / {@code GTE} / {@code LT} / {@code LTE} are always compared numerically, which
 *       overrides the primitive's own format;
 *   <li>{@code EQ} / {@code NEQ} compare a single whole value, and are therefore the only operators
 *       the type's format applies to.
 * </ul>
 */
public final class PrimitiveFormatValidator {

  /**
   * Operators the primitive's own format applies to. Exposed so the API descriptor and the frontend
   * share the exact same {@code appliesTo} set instead of restating it.
   */
  public static final Set<ConditionType> FORMAT_OPERATORS =
      Set.of(ConditionType.EQ, ConditionType.NEQ);

  /** Operators always compared numerically, whatever the primitive type. */
  public static final Set<ConditionType> NUMERIC_OPERATORS =
      Set.of(ConditionType.GT, ConditionType.GTE, ConditionType.LT, ConditionType.LTE);

  /** Operators that take no value. */
  private static final Set<ConditionType> UNARY_OPERATORS =
      Set.of(ConditionType.IS_NULL, ConditionType.IS_NOT_NULL);

  private PrimitiveFormatValidator() {}

  /**
   * Decides whether {@code value} is acceptable for {@code type} when compared with {@code
   * operator}.
   *
   * @param type primitive type of the inspected field, {@code null} means unknown and accepts
   *     anything
   * @param operator comparison operator, {@code null} means the value stands alone (scope variable,
   *     payload argument) and is validated as an equality would be
   * @param value candidate value
   * @return {@code true} when the value is acceptable
   */
  public static boolean isAccepted(PrimitiveType type, ConditionType operator, String value) {
    if (type == null) {
      return true;
    }
    if (operator != null) {
      if (UNARY_OPERATORS.contains(operator)) {
        return true;
      }
      if (NUMERIC_OPERATORS.contains(operator)) {
        return FormatRuleKind.NUMBER.matches(value);
      }
      if (!FORMAT_OPERATORS.contains(operator)) {
        // AND / OR are grouping nodes, MAPPER and DEPEND_ON are not value comparisons, and
        // IN / NIN match on substrings so partial values must stay valid.
        return true;
      }
    }
    return matchesPolicy(PrimitiveTypePolicy.of(type), value);
  }

  /**
   * Format acceptance on the runtime ingestion path, where only policies marked {@link
   * FormatEnforcement#RUNTIME_AND_INPUT} apply.
   *
   * <p>Rules introduced after a type started accepting free values stay {@link
   * FormatEnforcement#INPUT_ONLY} on purpose: rejecting them here would silently discard data
   * emitted by a third-party injector rather than surface an error to a user.
   */
  public static boolean isAcceptedAtRuntime(PrimitiveType type, String value) {
    if (type == null) {
      return true;
    }
    TypePolicy policy = PrimitiveTypePolicy.of(type);
    if (policy.enforcement() != FormatEnforcement.RUNTIME_AND_INPUT) {
      return true;
    }
    return matchesPolicy(policy, value);
  }

  private static boolean matchesPolicy(TypePolicy policy, String value) {
    if (!policy.hasRules()) {
      return true;
    }
    return policy.rules().stream().anyMatch(rule -> rule.matches(value));
  }
}
