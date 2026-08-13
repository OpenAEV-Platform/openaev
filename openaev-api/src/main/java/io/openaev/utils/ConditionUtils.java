package io.openaev.utils;

import io.openaev.database.model.Condition;
import io.openaev.database.model.ConditionType;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ConditionUtils {

  /**
   * Checks whether the condition is a mapper condition.
   *
   * @param condition condition to evaluate
   * @return {@code true} if the condition type is MAPPER
   */
  public boolean isMapperCondition(Condition condition) {
    return condition.getType() != null && ConditionType.MAPPER.equals(condition.getType());
  }

  /**
   * @return null (todo: implement)
   */
  public Condition isMapperConditionValid(Condition condition, String input, String data) {
    return null;
  }

  /**
   * Checks whether the condition is a dependency condition.
   *
   * @param condition condition to evaluate
   * @return {@code true} if the condition type is DEPEND_ON
   */
  public boolean isDependOnCondition(Condition condition) {
    return condition.getType() != null && condition.getType() == ConditionType.DEPEND_ON;
  }

  /**
   * Checks whether the condition is a filter condition.
   *
   * @param condition condition to evaluate
   * @return {@code true} if it is a data-filtering condition (not time, mapper, or dependency)
   */
  public boolean isFilterCondition(Condition condition) {
    if (condition.getType() == null) {
      return false;
    }
    return switch (condition.getType()) {
      case ConditionType.MAPPER, ConditionType.DEPEND_ON -> false;
      default -> true;
    };
  }

  public boolean isFilterConditionValid(String value, Condition rootFilter) {
    if (rootFilter == null) {
      return true;
    }

    // Handle Logical Groups (AND / OR)
    // If the condition has children, it's a logical operator node
    if (rootFilter.getType() == ConditionType.AND) {
      return rootFilter.getConditionChildren().stream()
          .allMatch(child -> isFilterConditionValid(value, child));
    }

    if (rootFilter.getType() == ConditionType.OR) {
      return rootFilter.getConditionChildren().stream()
          .anyMatch(child -> isFilterConditionValid(value, child));
    }

    // Handle Leaf Nodes
    // If it's not AND/OR, evaluate using existing switch logic
    return evaluateLeafCondition(value, rootFilter);
  }

  public boolean evaluateLeafCondition(String actualValue, Condition filter) {
    ConditionType type = filter.getType();
    if (type == null) {
      return true;
    }
    String target = filter.getValue();
    boolean caseSensitive = filter.isCaseSensitive();

    switch (type) {
      case IS_NULL:
        return actualValue == null;
      case IS_NOT_NULL:
        return actualValue != null;
      case EQ:
        return actualValue != null
            && (caseSensitive ? actualValue.equals(target) : actualValue.equalsIgnoreCase(target));
      case NEQ:
        return actualValue != null
            && (caseSensitive
                ? !actualValue.equals(target)
                : !actualValue.equalsIgnoreCase(target));
      case IN, NIN:
        if (actualValue == null || target == null) {
          return false;
        }
        String normalizedActualValue =
            caseSensitive ? actualValue : actualValue.toLowerCase(Locale.ROOT);
        List<String> normalizedTargets =
            Arrays.stream(target.split(","))
                .map(String::trim)
                .filter(candidate -> !candidate.isBlank())
                .map(candidate -> caseSensitive ? candidate : candidate.toLowerCase(Locale.ROOT))
                .toList();
        boolean contains = normalizedTargets.stream().anyMatch(normalizedActualValue::contains);
        return (type == ConditionType.IN) == contains;
      case GT, GTE, LT, LTE:
        return handleNumericComparison(actualValue, target, type);
      default:
        return true;
    }
  }

  /**
   * Checks whether a value matches any same-key-type leaf condition in the condition tree,
   * ignoring AND/OR logical grouping. This is used for propagation (deciding which values are
   * relevant to an event), not for full evaluation (deciding if the event is fully satisfied).
   *
   * <p>A leaf is only checked when it targets {@code keyTypeName}: e.g. a "host is not null" leaf
   * must never be satisfied by a port value, or unrelated values leak into the event's pool.
   *
   * @param value the value to check
   * @param node the condition tree node to inspect
   * @param keyTypeName the key type {@code value} was extracted for
   * @return {@code true} if the value satisfies at least one same-key-type leaf condition
   */
  public boolean matchesAnyLeafCondition(String value, Condition node, String keyTypeName) {
    if (node == null || node.getType() == null) {
      return false;
    }
    // For logical operator nodes (AND/OR), recurse into children looking for any matching leaf
    if (node.getType() == ConditionType.AND || node.getType() == ConditionType.OR) {
      return node.getConditionChildren() != null
          && node.getConditionChildren().stream()
              .anyMatch(child -> matchesAnyLeafCondition(value, child, keyTypeName));
    }
    // A leaf only applies to the key type(s) it was configured for.
    if (node.getKeyTypes() != null
        && !node.getKeyTypes().isEmpty()
        && node.getKeyTypes().stream().noneMatch(kt -> kt.name().equals(keyTypeName))) {
      return false;
    }
    // For leaf nodes, delegate to the existing leaf evaluator
    return evaluateLeafCondition(value, node);
  }

  private static boolean handleNumericComparison(
      String actualValue, String target, ConditionType type) {
    if (actualValue == null || target == null) {
      return false;
    }
    try {
      double actualNum = Double.parseDouble(actualValue);
      double targetNum = Double.parseDouble(target);
      if (type == ConditionType.GT) {
        return actualNum > targetNum;
      }
      if (type == ConditionType.GTE) {
        return actualNum >= targetNum;
      }
      if (type == ConditionType.LT) {
        return actualNum < targetNum;
      }
      return actualNum <= targetNum;
    } catch (NumberFormatException e) {
      log.warn("Numeric comparison failed for value: {} against target: {}", actualValue, target);
      return false;
    }
  }
}
