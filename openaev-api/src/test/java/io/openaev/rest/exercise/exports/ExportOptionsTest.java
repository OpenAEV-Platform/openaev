package io.openaev.rest.exercise.exports;

import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import io.openaev.IntegrationTest;
import org.junit.jupiter.api.*;

@TestInstance(PER_CLASS)
public class ExportOptionsTest extends IntegrationTest {

  @DisplayName("Given three boolean options, the resulting mask equals the correct decimal value")
  @Test
  public void given_three_boolean_options_the_resulting_mask_equals_correct_decimal_value() {
    Assertions.assertEquals(0, ExportOptions.mask(false, false, false));
    Assertions.assertEquals(1, ExportOptions.mask(true, false, false));
    Assertions.assertEquals(2, ExportOptions.mask(false, true, false));
    Assertions.assertEquals(3, ExportOptions.mask(true, true, false));
    Assertions.assertEquals(4, ExportOptions.mask(false, false, true));
    Assertions.assertEquals(5, ExportOptions.mask(true, false, true));
    Assertions.assertEquals(6, ExportOptions.mask(false, true, true));
    Assertions.assertEquals(7, ExportOptions.mask(true, true, true));
  }

  @DisplayName(
      "Given a mask and a standalone option, return correctly whether the option is within the mask or not")
  @Test
  public void
      given_a_mask_and_a_standalone_option_return_correctly_whether_the_option_is_within_the_mask_or_not() {
    Assertions.assertFalse(
        ExportOptions.has(ExportOptions.WITH_PLAYERS, ExportOptions.mask(false, false, false)));
    Assertions.assertFalse(
        ExportOptions.has(ExportOptions.WITH_PLAYERS, ExportOptions.mask(false, true, false)));
    Assertions.assertFalse(
        ExportOptions.has(ExportOptions.WITH_PLAYERS, ExportOptions.mask(false, false, true)));
    Assertions.assertFalse(
        ExportOptions.has(ExportOptions.WITH_PLAYERS, ExportOptions.mask(false, true, true)));
    Assertions.assertTrue(
        ExportOptions.has(ExportOptions.WITH_PLAYERS, ExportOptions.mask(true, false, false)));
    Assertions.assertTrue(
        ExportOptions.has(ExportOptions.WITH_PLAYERS, ExportOptions.mask(true, true, false)));
    Assertions.assertTrue(
        ExportOptions.has(ExportOptions.WITH_PLAYERS, ExportOptions.mask(true, false, true)));
    Assertions.assertTrue(
        ExportOptions.has(ExportOptions.WITH_PLAYERS, ExportOptions.mask(true, true, true)));

    Assertions.assertFalse(
        ExportOptions.has(ExportOptions.WITH_TEAMS, ExportOptions.mask(false, false, false)));
    Assertions.assertTrue(
        ExportOptions.has(ExportOptions.WITH_TEAMS, ExportOptions.mask(false, true, false)));
    Assertions.assertFalse(
        ExportOptions.has(ExportOptions.WITH_TEAMS, ExportOptions.mask(false, false, true)));
    Assertions.assertTrue(
        ExportOptions.has(ExportOptions.WITH_TEAMS, ExportOptions.mask(false, true, true)));
    Assertions.assertFalse(
        ExportOptions.has(ExportOptions.WITH_TEAMS, ExportOptions.mask(true, false, false)));
    Assertions.assertTrue(
        ExportOptions.has(ExportOptions.WITH_TEAMS, ExportOptions.mask(true, true, false)));
    Assertions.assertFalse(
        ExportOptions.has(ExportOptions.WITH_TEAMS, ExportOptions.mask(true, false, true)));
    Assertions.assertTrue(
        ExportOptions.has(ExportOptions.WITH_TEAMS, ExportOptions.mask(true, true, true)));

    Assertions.assertFalse(
        ExportOptions.has(
            ExportOptions.WITH_VARIABLE_VALUES, ExportOptions.mask(false, false, false)));
    Assertions.assertFalse(
        ExportOptions.has(
            ExportOptions.WITH_VARIABLE_VALUES, ExportOptions.mask(false, true, false)));
    Assertions.assertTrue(
        ExportOptions.has(
            ExportOptions.WITH_VARIABLE_VALUES, ExportOptions.mask(false, false, true)));
    Assertions.assertTrue(
        ExportOptions.has(
            ExportOptions.WITH_VARIABLE_VALUES, ExportOptions.mask(false, true, true)));
    Assertions.assertFalse(
        ExportOptions.has(
            ExportOptions.WITH_VARIABLE_VALUES, ExportOptions.mask(true, false, false)));
    Assertions.assertFalse(
        ExportOptions.has(
            ExportOptions.WITH_VARIABLE_VALUES, ExportOptions.mask(true, true, false)));
    Assertions.assertTrue(
        ExportOptions.has(
            ExportOptions.WITH_VARIABLE_VALUES, ExportOptions.mask(true, false, true)));
    Assertions.assertTrue(
        ExportOptions.has(
            ExportOptions.WITH_VARIABLE_VALUES, ExportOptions.mask(true, true, true)));
  }
}
