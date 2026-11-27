package io.openaev.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("CsvInjectionUtils: a spreadsheet must not run a cell as a formula")
class CsvInjectionUtilsTest {

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"=SUM(A1)", "+1", "-1", "@cmd", "\tx", "\rx", "\nx", "'=A", "''-A"})
  @DisplayName("given_aValueStartingAFormula_should_putAQuoteInFrontAndTakeItBackOff")
  void given_aValueStartingAFormula_should_putAQuoteInFrontAndTakeItBackOff(String value) {
    // Act
    String neutralised = CsvInjectionUtils.neutralise(value);

    // Assert
    assertEquals("'" + value, neutralised);
    assertEquals(value, CsvInjectionUtils.undoNeutralisation(neutralised));
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"", "TLP:AMBER", "a=b", "'", "'TLP", "O'NEIL"})
  @DisplayName("given_aValueThatIsNotAFormula_should_leaveItUntouchedBothWays")
  void given_aValueThatIsNotAFormula_should_leaveItUntouchedBothWays(String value) {
    // Act / Assert
    assertEquals(value, CsvInjectionUtils.neutralise(value));
    assertEquals(value, CsvInjectionUtils.undoNeutralisation(value));
  }

  @Test
  @DisplayName("given_null_should_stayNull")
  void given_null_should_stayNull() {
    // Act / Assert
    assertNull(CsvInjectionUtils.neutralise(null));
    assertNull(CsvInjectionUtils.undoNeutralisation(null));
  }
}
