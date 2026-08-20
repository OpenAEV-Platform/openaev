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

  @Test
  @DisplayName(
      "given AND(port==445, host is not null), a Port value must not match through the Host leaf")
  void given_andOfDifferentKeyTypeLeaves_should_notMatchValueThroughUnrelatedLeaf() {
    // Arrange — mirrors a step gated by "port == 445 AND host is not null"
    Condition portEq445 =
        Condition.builder()
            .type(ConditionType.EQ)
            .keyTypes(List.of(PrimitiveType.Port))
            .value("445")
            .build();
    Condition hostIsNotNull =
        Condition.builder()
            .type(ConditionType.IS_NOT_NULL)
            .keyTypes(List.of(PrimitiveType.Host))
            .build();
    Condition and =
        Condition.builder()
            .type(ConditionType.AND)
            .conditionChildren(List.of(portEq445, hostIsNotNull))
            .build();

    // Act + Assert
    // "5" is a Port value: must fail (only the Host leaf trivially accepts non-null strings).
    assertFalse(conditionUtils.matchesAnyLeafCondition("5", and, "Port"));
    // "445" is a Port value that satisfies the Port leaf directly.
    assertTrue(conditionUtils.matchesAnyLeafCondition("445", and, "Port"));
    // Any non-null string is a legitimate Host value for the Host leaf.
    assertTrue(conditionUtils.matchesAnyLeafCondition("10.0.0.1", and, "Host"));
  }
}
