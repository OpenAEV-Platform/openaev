package io.openaev.api.marking_definition;

import static io.openaev.utils.fixtures.MarkingDefinitionFixture.createMarkingDefinition;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.openaev.api.marking_definition.dto.MarkingDefinitionCsvRow;
import io.openaev.database.model.MarkingDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("MarkingDefinitionMapper.toCsvRow")
class MarkingDefinitionMapperTest {

  @Test
  @DisplayName("given_aMarking_should_giveTheFourColumnsAsTextWithNoIdOrTenant")
  void given_aMarking_should_giveTheFourColumnsAsTextWithNoIdOrTenant() {
    // Arrange
    MarkingDefinition marking = createMarkingDefinition("TLP", "TLP:AMBER", 3, "#ffc107");

    // Act
    MarkingDefinitionCsvRow row = MarkingDefinitionMapper.toCsvRow(marking);

    // Assert
    assertEquals("TLP", row.getType());
    assertEquals("TLP:AMBER", row.getDefinition());
    assertEquals("#ffc107", row.getColor());
    assertEquals("3", row.getOrder());
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"=SUM(A1)", "+1", "-1", "@cmd", "\tx", "\rx"})
  @DisplayName("given_aTypeOrDefinitionStartingAFormula_should_putAQuoteInFront")
  void given_aTypeOrDefinitionStartingAFormula_should_putAQuoteInFront(String value) {
    // Arrange
    MarkingDefinition marking = createMarkingDefinition(value, value, 1, "#ffc107");

    // Act
    MarkingDefinitionCsvRow row = MarkingDefinitionMapper.toCsvRow(marking);

    // Assert
    assertEquals("'" + value, row.getType());
    assertEquals("'" + value, row.getDefinition());
  }
}
