package io.openaev.utils;

import io.openaev.database.model.Condition;
import io.openaev.database.model.ConditionType;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ConditionUtils {

  /**
   * Checks whether the condition is a time-based condition.
   *
   * @param condition condition to evaluate
   * @return {@code true} if the condition type is AFTER or BEFORE
   */
  public boolean isTimeCondition(Condition condition) {
    return switch (condition.getType()) {
      case ConditionType.AFTER, ConditionType.BEFORE -> true;
      default -> false;
    };
  }

  /**
   * Evaluates a time condition against the current time.
   *
   * <p>TODO: this is for legacy behavior only (compare from start of workflow instead of previous
   * step.
   *
   * @param conditionTemplate the condition to evaluate
   * @param now current instant
   * @param goal target instant
   * @return {@code true} if the condition is valid
   */
  public Boolean isTimeConditionValid(Condition conditionTemplate, Instant now, Instant goal) {
    if (conditionTemplate.getType().equals(ConditionType.AFTER)) {
      return now.isAfter(goal);
    } else if (conditionTemplate.getType().equals(ConditionType.BEFORE)) {
      return now.isBefore(goal);
    }
    return false;
  }

  /**
   * Checks whether the condition is a mapper condition.
   *
   * @param condition condition to evaluate
   * @return {@code true} if the condition type is MAPPER
   */
  public boolean isMapperCondition(Condition condition) {
    return condition.getType() == ConditionType.MAPPER;
  }

  /**
   * @return null (todo: implement)
   */
  public Condition isMapperConditionValid(Condition condition, String input, String data) {
    return null;
  }

  /**
   * Checks whether the condition is a filter condition.
   *
   * @param condition condition to evaluate
   * @return {@code true} if it is not a time or mapper condition
   */
  public boolean isFilterCondition(Condition condition) {
    return switch (condition.getType()) {
      case ConditionType.AFTER, ConditionType.BEFORE, ConditionType.MAPPER -> false;
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

  private boolean evaluateLeafCondition(String actualValue, Condition filter) {
    ConditionType type = filter.getType();
    String target = filter.getValue();

    switch (type) {
      case IS_NULL:
        return actualValue == null;
      case IS_NOT_NULL:
        return actualValue != null;
      case EQ:
        return actualValue != null && actualValue.equalsIgnoreCase(target);
      case NEQ:
        return actualValue != null && !actualValue.equalsIgnoreCase(target);
      case IN, NIN:
        if (actualValue == null || target == null) {
          return false;
        }
        List<String> targetList = Arrays.asList(target.split("\\s*,\\s*"));
        boolean contains = targetList.stream().anyMatch(actualValue::equalsIgnoreCase);
        return (type == ConditionType.IN) == contains;
      case GT, GTE, LT, LTE:
        return handleNumericComparison(actualValue, target, type);
      default:
        return true;
    }
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
