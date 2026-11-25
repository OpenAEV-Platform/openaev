package io.openaev.api.marking_definition.form;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MarkingDefinitionInput")
class MarkingDefinitionInputTest {

  // The width of the type and definition columns.
  private static final int COLUMN_WIDTH = 255;
  private static final Validator VALIDATOR =
      Validation.buildDefaultValidatorFactory().getValidator();

  private static Set<String> invalidProperties(String type, String definition) {
    return VALIDATOR.validate(new MarkingDefinitionInput(type, definition, "#4CAF50", 1)).stream()
        .map(violation -> violation.getPropertyPath().toString())
        .collect(Collectors.toSet());
  }

  @Test
  @DisplayName("given_aTypeOrDefinitionOfTheColumnWidth_should_beValid")
  void given_aTypeOrDefinitionOfTheColumnWidth_should_beValid() {
    // Act
    Set<String> invalid = invalidProperties("T".repeat(COLUMN_WIDTH), "D".repeat(COLUMN_WIDTH));

    // Assert
    assertEquals(Set.of(), invalid);
  }

  @Test
  @DisplayName("given_aTypeOrDefinitionBeyondTheColumnWidth_should_beRefused")
  void given_aTypeOrDefinitionBeyondTheColumnWidth_should_beRefused() {
    // Act
    Set<String> invalid =
        invalidProperties("T".repeat(COLUMN_WIDTH + 1), "D".repeat(COLUMN_WIDTH + 1));

    // Assert
    assertEquals(Set.of("type", "definition"), invalid);
  }
}
