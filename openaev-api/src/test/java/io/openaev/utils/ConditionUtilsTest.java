package io.openaev.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.database.model.Condition;
import io.openaev.database.model.ConditionType;
import io.openaev.database.model.PrimitiveType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Condition utils")
class ConditionUtilsTest {

  private final ConditionUtils conditionUtils = new ConditionUtils();

  @Test
  @DisplayName("given action output and IN should match by substring")
  void given_actionOutputAndIn_should_matchBySubstring() {
    // Arrange
    Condition condition =
        Condition.builder()
            .type(ConditionType.IN)
            .keyTypes(List.of(PrimitiveType.ActionOutput))
            .value("WINTERFELL")
            .caseSensitive(false)
            .build();
    String actualValue =
        "NetExec succeeded:\n"
            + "SMB 74.234.220.121 445 WINTERFELL [*] Windows 10 / Server 2019 Build 17763";

    // Act
    boolean result = conditionUtils.evaluateLeafCondition(actualValue, condition);

    // Assert
    assertTrue(result);
  }

  @Test
  @DisplayName("given action output and NIN should fail when substring exists")
  void given_actionOutputAndNin_should_failWhenSubstringExists() {
    // Arrange
    Condition condition =
        Condition.builder()
            .type(ConditionType.NIN)
            .keyTypes(List.of(PrimitiveType.ActionOutput))
            .value("WINTERFELL")
            .caseSensitive(true)
            .build();
    String actualValue = "SMB 74.234.220.121 445 WINTERFELL [*] Windows 10";

    // Act
    boolean result = conditionUtils.evaluateLeafCondition(actualValue, condition);

    // Assert
    assertFalse(result);
  }

  @Test
  @DisplayName("given non action output and IN should match by substring")
  void given_nonActionOutputAndIn_should_matchBySubstring() {
    // Arrange
    Condition condition =
        Condition.builder()
            .type(ConditionType.IN)
            .keyTypes(List.of(PrimitiveType.Text))
            .value("WINTERFELL")
            .caseSensitive(false)
            .build();
    String actualValue = "SMB 74.234.220.121 445 WINTERFELL [*] Windows 10";

    // Act
    boolean result = conditionUtils.evaluateLeafCondition(actualValue, condition);

    // Assert
    assertTrue(result);
  }

  @Test
  @DisplayName("given NEQ should keep exact non-equality behavior")
  void given_neq_should_keepExactNonEqualityBehavior() {
    // Arrange
    Condition condition =
        Condition.builder()
            .type(ConditionType.NEQ)
            .keyTypes(List.of(PrimitiveType.Text))
            .value("WINTER")
            .caseSensitive(true)
            .build();
    String actualValue = "WINTERFELL";

    // Act
    boolean result = conditionUtils.evaluateLeafCondition(actualValue, condition);

    // Assert
    assertTrue(result);
  }
}
