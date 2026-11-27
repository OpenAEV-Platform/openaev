package io.openaev.service.marking_definition;

import static io.openaev.service.marking_definition.MarkingImportBatch.PARAM_COLOR;
import static io.openaev.service.marking_definition.MarkingImportBatch.PARAM_COLUMN;
import static io.openaev.service.marking_definition.MarkingImportBatch.PARAM_EARLIER_ROW;
import static io.openaev.service.marking_definition.MarkingImportBatch.PARAM_HIGHEST_ORDER;
import static io.openaev.service.marking_definition.MarkingImportBatch.PARAM_KNOWN_COLOR;
import static io.openaev.service.marking_definition.MarkingImportBatch.PARAM_KNOWN_ORDER;
import static io.openaev.service.marking_definition.MarkingImportBatch.PARAM_MAX_LENGTH;
import static io.openaev.service.marking_definition.MarkingImportBatch.PARAM_ORDER;
import static io.openaev.utils.fixtures.MarkingDefinitionFixture.createMarkingDefinition;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.database.model.MarkingDefinition;
import io.openaev.database.model.MarkingDefinitionStixIds;
import io.openaev.database.model.Tenant;
import io.openaev.service.import_report.ImportResultRow;
import io.openaev.service.import_report.ImportRowReason;
import io.openaev.service.import_report.ImportRowStatus;
import io.openaev.service.import_report.RowImportResult;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The rules applied to each row of an imported file. The batch reads and writes nothing, so this
 * test needs no database and no mock: the service test only covers what the service hands the batch
 * (the clearance) and what it does with the result (the save).
 */
class MarkingImportBatchTest {

  private static final String TENANT_ID = "tenant-1";
  private static final Validator VALIDATOR =
      Validation.buildDefaultValidatorFactory().getValidator();
  // Held by the first admin, whose levels a set of today's definitions cannot express.
  private static final Clearance EVERY_LEVEL = Clearance.everyLevel();

  // The seeded TLP scale of a tenant.
  private final MarkingDefinition tlpClear = marking("clear", "TLP", "TLP:CLEAR", 1, "#ffffff");
  private final MarkingDefinition tlpGreen = marking("green", "TLP", "TLP:GREEN", 2, "#4caf50");
  private final MarkingDefinition tlpAmber = marking("amber", "TLP", "TLP:AMBER", 3, "#ffc107");
  private final MarkingDefinition tlpRed = marking("red", "TLP", "TLP:RED", 4, "#ff0000");

  private static MarkingDefinition marking(
      String id, String type, String definition, int order, String color) {
    MarkingDefinition marking = createMarkingDefinition(type, definition, order, color);
    marking.setId(id);
    marking.setTenant(new Tenant(TENANT_ID));
    return marking;
  }

  private static MarkingRow row(
      int position, String type, String definition, String color, String order) {
    return new MarkingRow(position, type, definition, color, order);
  }

  private static MarkingImportBatch batch(
      List<MarkingDefinition> existing, List<MarkingDefinition> held) {
    return batch(existing, Clearance.of(held));
  }

  private static MarkingImportBatch batch(List<MarkingDefinition> existing, Clearance clearance) {
    return new MarkingImportBatch(TENANT_ID, existing, clearance, VALIDATOR);
  }

  private static RowImportResult run(
      List<MarkingDefinition> existing, List<MarkingDefinition> held, MarkingRow... rows) {
    return run(existing, Clearance.of(held), rows);
  }

  private static RowImportResult run(
      List<MarkingDefinition> existing, Clearance clearance, MarkingRow... rows) {
    return batch(existing, clearance).importRows(List.of(rows)).result();
  }

  private static ImportResultRow at(RowImportResult result, int position) {
    return result.rows().get(position - 1);
  }

  private static void assertSkipped(
      ImportResultRow row, ImportRowReason reason, Map<String, String> params) {
    assertEquals(ImportRowStatus.SKIPPED, row.status());
    assertEquals(reason, row.reason());
    assertEquals(params, row.params());
  }

  @Nested
  @DisplayName("created markings")
  class Created {

    @Test
    @DisplayName("given_validRows_should_createThemNormalizedAndUnprotected")
    void given_validRows_should_createThemNormalizedAndUnprotected() {
      // Arrange
      MarkingImportBatch batch = batch(List.of(tlpClear), EVERY_LEVEL);

      // Act
      MarkingImportBatch.Outcome outcome =
          batch.importRows(
              List.of(
                  row(1, " pap ", "pap:amber", "#ffc107", "3"),
                  row(2, "PAP", "PAP:RED", "#FF0000", "4")));

      // Assert
      RowImportResult result = outcome.result();
      assertEquals(2, result.created());
      assertEquals(0, result.skipped());
      assertEquals(List.of("type", "definition", "color", "order"), result.columns());
      List<MarkingDefinition> created = outcome.created();
      assertEquals(
          List.of("PAP:AMBER", "PAP:RED"),
          created.stream().map(MarkingDefinition::getDefinition).toList());
      assertTrue(created.stream().allMatch(marking -> "PAP".equals(marking.getType())));
      assertTrue(created.stream().noneMatch(MarkingDefinition::getProtectedDefinition));
      assertTrue(
          created.stream().allMatch(marking -> TENANT_ID.equals(marking.getTenant().getId())));
    }

    @Test
    @DisplayName("given_validRows_should_carryTheStixIdOpenCtiGivesThem")
    void given_validRows_should_carryTheStixIdOpenCtiGivesThem() {
      // Arrange
      MarkingImportBatch batch = batch(List.of(tlpClear), EVERY_LEVEL);

      // Act
      MarkingImportBatch.Outcome outcome =
          batch.importRows(
              List.of(
                  row(1, "pap", "pap:amber", "#ffc107", "3"),
                  row(2, "TLP", "TLP:GREEN", "#4caf50", "2")));

      // Assert
      assertEquals(
          List.of(
              MarkingDefinitionStixIds.generate("PAP", "PAP:AMBER"),
              "marking-definition--34098fce-860f-48ae-8e50-ebd3cc5e41da"),
          outcome.created().stream().map(MarkingDefinition::getStixId).toList());
    }

    @Test
    @DisplayName("given_aStixIdCarriedByTheTenant_should_createWithoutOne")
    void given_aStixIdCarriedByTheTenant_should_createWithoutOne() {
      // Arrange - TLP:WHITE next to TLP:CLEAR: OpenCTI names both with one id.
      tlpClear.setStixId("marking-definition--613f2e26-407d-48c7-9eca-b8e91df99dc9");
      MarkingImportBatch batch = batch(List.of(tlpClear), EVERY_LEVEL);

      // Act
      MarkingImportBatch.Outcome outcome =
          batch.importRows(List.of(row(1, "TLP", "TLP:WHITE", "#ffffff", "5")));

      // Assert
      assertNull(outcome.created().get(0).getStixId());
    }

    @Test
    @DisplayName("given_aStixIdGivenByAnEarlierRow_should_createWithoutOne")
    void given_aStixIdGivenByAnEarlierRow_should_createWithoutOne() {
      // Arrange
      MarkingImportBatch batch = batch(List.of(tlpGreen), EVERY_LEVEL);

      // Act
      MarkingImportBatch.Outcome outcome =
          batch.importRows(
              List.of(
                  row(1, "TLP", "TLP:CLEAR", "#ffffff", "1"),
                  row(2, "TLP", "TLP:WHITE", "#ffffff", "5")));

      // Assert
      List<MarkingDefinition> created = outcome.created();
      assertEquals(
          "marking-definition--613f2e26-407d-48c7-9eca-b8e91df99dc9", created.get(0).getStixId());
      assertNull(created.get(1).getStixId());
    }
  }

  @Nested
  @DisplayName("report of the rows")
  class Report {

    @Test
    @DisplayName("given_aMixedFile_should_createTheValidRowsSkipTheOthersAndKeepPositions")
    void given_aMixedFile_should_createTheValidRowsSkipTheOthersAndKeepPositions() {
      // Act
      RowImportResult result =
          run(
              List.of(tlpAmber),
              EVERY_LEVEL,
              row(1, "PAP", "PAP:CLEAR", "#ffffff", "1"),
              row(2, "", "PAP:GREEN", "#4caf50", "2"),
              row(3, "TLP", "TLP:AMBER", "#ffc107", "3"),
              row(4, "PAP", "PAP:RED", "#ff0000", "4"));

      // Assert
      assertEquals(2, result.created());
      assertEquals(1, result.skipped());
      assertEquals(1, result.ignored());
      assertEquals(
          List.of(
              ImportRowStatus.CREATED,
              ImportRowStatus.SKIPPED,
              ImportRowStatus.IGNORED,
              ImportRowStatus.CREATED),
          result.rows().stream().map(ImportResultRow::status).toList());
      assertEquals(
          List.of(1, 2, 3, 4), result.rows().stream().map(ImportResultRow::position).toList());
    }

    @Test
    @DisplayName("given_aRow_should_echoItsValuesAsReadByFieldName")
    void given_aRow_should_echoItsValuesAsReadByFieldName() {
      // Act
      RowImportResult result =
          run(List.of(tlpClear), List.of(tlpClear), row(1, "TLP", "TLP:X", "not-a-color", "3"));

      // Assert
      assertEquals(
          Map.of("type", "TLP", "definition", "TLP:X", "color", "not-a-color", "order", "3"),
          at(result, 1).values());
      assertEquals(
          List.of("type", "definition", "color", "order"),
          List.copyOf(at(result, 1).values().keySet()));
    }
  }

  @Nested
  @DisplayName("row validation, first failure wins")
  class RowValidation {

    @ParameterizedTest(name = "[{index}] type={0} definition={1} color={2} order={3}")
    @CsvSource(
        value = {
          "'',TLP:X,#ffffff,1",
          "TLP,'',#ffffff,1",
          "TLP,TLP:X,'',1",
          "TLP,TLP:X,#ffffff,''"
        })
    @DisplayName("given_anEmptyValue_should_skipAsMissingField")
    void given_anEmptyValue_should_skipAsMissingField(
        String type, String definition, String color, String order) {
      // Act
      RowImportResult result =
          run(List.of(tlpClear), List.of(tlpClear), row(1, type, definition, color, order));

      // Assert
      assertSkipped(at(result, 1), ImportRowReason.MISSING_FIELD, Map.of());
    }

    @Test
    @DisplayName("given_aTypeOrDefinitionLongerThanTheColumn_should_skipAsValueTooLongNotThrow")
    void given_aTypeOrDefinitionLongerThanTheColumn_should_skipAsValueTooLongNotThrow() {
      // Arrange
      MarkingImportBatch batch = batch(List.of(tlpClear), List.of(tlpClear));
      String tooLong = "X".repeat(256);

      // Act
      MarkingImportBatch.Outcome outcome =
          batch.importRows(
              List.of(
                  row(1, "TLP", tooLong, "#ffffff", "1"),
                  row(2, tooLong, "TLP:OK", "#ffffff", "1")));

      // Assert
      RowImportResult result = outcome.result();
      Map<String, String> params = Map.of(PARAM_MAX_LENGTH, "255");
      assertSkipped(at(result, 1), ImportRowReason.VALUE_TOO_LONG, params);
      assertSkipped(at(result, 2), ImportRowReason.VALUE_TOO_LONG, params);
      assertTrue(outcome.created().isEmpty());
    }

    @Test
    @DisplayName("given_severalProblemsInOneRow_should_reportTheFirstByReasonThenByColumn")
    void given_severalProblemsInOneRow_should_reportTheFirstByReasonThenByColumn() {
      // Arrange
      String tooLong = "X".repeat(256);

      // Act
      RowImportResult result =
          run(
              List.of(tlpClear),
              List.of(tlpClear),
              row(1, "", tooLong, "red", "1"),
              row(2, tooLong, "TLP:OK", "red", "abc"),
              row(3, "TLP", "TLP:OK", "red", "abc"),
              row(4, "TLP", "TLP:OK", "red", ""));

      // Assert
      assertSkipped(at(result, 1), ImportRowReason.MISSING_FIELD, Map.of());
      assertSkipped(at(result, 2), ImportRowReason.VALUE_TOO_LONG, Map.of(PARAM_MAX_LENGTH, "255"));
      assertSkipped(at(result, 3), ImportRowReason.INVALID_VALUE, Map.of(PARAM_COLUMN, "color"));
      assertSkipped(at(result, 4), ImportRowReason.MISSING_FIELD, Map.of());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"red", "#fff", "#ggg000", "ffffff", "#ffffff0"})
    @DisplayName("given_aColorThatIsNotAHexValue_should_skipAsInvalidColor")
    void given_aColorThatIsNotAHexValue_should_skipAsInvalidColor(String color) {
      // Act
      RowImportResult result =
          run(List.of(tlpClear), List.of(tlpClear), row(1, "TLP", "TLP:X", color, "1"));

      // Assert
      assertSkipped(at(result, 1), ImportRowReason.INVALID_VALUE, Map.of(PARAM_COLUMN, "color"));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"3.5", "abc", "-1", "1e3", "99999999999", " 3"})
    @DisplayName("given_anOrderThatIsNotAWholeNumber_should_skipAsInvalidOrder")
    void given_anOrderThatIsNotAWholeNumber_should_skipAsInvalidOrder(String order) {
      // Act
      RowImportResult result =
          run(List.of(tlpClear), List.of(tlpClear), row(1, "TLP", "TLP:X", "#ffffff", order));

      // Assert
      assertSkipped(at(result, 1), ImportRowReason.INVALID_VALUE, Map.of(PARAM_COLUMN, "order"));
    }
  }

  @Nested
  @DisplayName("same type and definition as another marking, never overwritten")
  class SameKey {

    @Test
    @DisplayName("given_anExistingMarkingWithAnotherColor_should_conflictWithTheColors")
    void given_anExistingMarkingWithAnotherColor_should_conflictWithTheColors() {
      // Act
      RowImportResult result =
          run(List.of(tlpAmber), List.of(tlpAmber), row(1, "tlp", "tlp:amber", "#ff0000", "3"));

      // Assert
      assertSkipped(
          at(result, 1),
          ImportRowReason.CONFLICT,
          Map.of(PARAM_COLOR, "#ff0000", PARAM_KNOWN_COLOR, "#ffc107"));
    }

    @Test
    @DisplayName("given_anExistingMarkingWithAnotherOrder_should_conflictWithTheOrders")
    void given_anExistingMarkingWithAnotherOrder_should_conflictWithTheOrders() {
      // Act
      RowImportResult result =
          run(List.of(tlpAmber), List.of(tlpAmber), row(1, "TLP", "TLP:AMBER", "#ffc107", "9"));

      // Assert
      assertSkipped(
          at(result, 1),
          ImportRowReason.CONFLICT,
          Map.of(PARAM_ORDER, "9", PARAM_KNOWN_ORDER, "3"));
    }

    @Test
    @DisplayName("given_aProtectedMarkingWithBothDifferent_should_conflictWithBoth")
    void given_aProtectedMarkingWithBothDifferent_should_conflictWithBoth() {
      // Arrange
      MarkingDefinition protectedAmber = marking("p", "TLP", "TLP:AMBER", 3, "#ffc107");
      protectedAmber.setProtectedDefinition(true);
      MarkingImportBatch batch = batch(List.of(protectedAmber), List.of(protectedAmber));

      // Act
      MarkingImportBatch.Outcome outcome =
          batch.importRows(List.of(row(1, "TLP", "TLP:AMBER", "#00ff00", "2")));

      // Assert
      RowImportResult result = outcome.result();
      assertSkipped(
          at(result, 1),
          ImportRowReason.CONFLICT,
          Map.of(
              PARAM_COLOR, "#00ff00",
              PARAM_KNOWN_COLOR, "#ffc107",
              PARAM_ORDER, "2",
              PARAM_KNOWN_ORDER, "3"));
      assertTrue(outcome.created().isEmpty());
    }

    @Test
    @DisplayName("given_anEarlierRowOfTheFile_should_conflictWithThatRow")
    void given_anEarlierRowOfTheFile_should_conflictWithThatRow() {
      // Act
      RowImportResult result =
          run(
              List.of(tlpClear),
              List.of(tlpClear),
              row(1, "PAP", "PAP:AMBER", "#ffc107", "2"),
              row(2, "PAP", "PAP:AMBER", "#ffc107", "5"),
              row(3, "PAP", "PAP:AMBER", "#00ff00", "2"));

      // Assert
      assertEquals(ImportRowStatus.CREATED, at(result, 1).status());
      assertSkipped(
          at(result, 2),
          ImportRowReason.CONFLICT,
          Map.of(PARAM_EARLIER_ROW, "1", PARAM_ORDER, "5", PARAM_KNOWN_ORDER, "2"));
      assertSkipped(
          at(result, 3),
          ImportRowReason.CONFLICT,
          Map.of(PARAM_EARLIER_ROW, "1", PARAM_COLOR, "#00ff00", PARAM_KNOWN_COLOR, "#ffc107"));
    }

    @Test
    @DisplayName("given_aRowIdenticalToAnExistingOrEarlierRow_should_ignoreItWithNoReason")
    void given_aRowIdenticalToAnExistingOrEarlierRow_should_ignoreItWithNoReason() {
      // Act
      RowImportResult result =
          run(
              List.of(tlpAmber),
              List.of(tlpAmber),
              row(1, "TLP", "TLP:AMBER", "#FFC107", "3"),
              row(2, "PAP", "PAP:AMBER", "#ffc107", "3"),
              row(3, "PAP", "PAP:AMBER", "#ffc107", "3"));

      // Assert
      assertEquals(1, result.created());
      assertEquals(0, result.skipped());
      assertEquals(2, result.ignored());
      assertEquals(ImportRowStatus.IGNORED, at(result, 1).status());
      assertNull(at(result, 1).reason());
      assertTrue(at(result, 1).params().isEmpty());
      assertEquals(ImportRowStatus.IGNORED, at(result, 3).status());
    }

    @Test
    @DisplayName("given_aKeyOfTheTenantAndAnEarlierRow_should_compareWithTheExistingMarking")
    void given_aKeyOfTheTenantAndAnEarlierRow_should_compareWithTheExistingMarking() {
      // Act
      RowImportResult result =
          run(
              List.of(tlpAmber),
              List.of(tlpAmber),
              row(1, "TLP", "TLP:AMBER", "#ff0000", "3"),
              row(2, "TLP", "TLP:AMBER", "#ffc107", "3"));

      // Assert
      assertEquals(ImportRowReason.CONFLICT, at(result, 1).reason());
      assertEquals(ImportRowStatus.IGNORED, at(result, 2).status());
    }
  }

  @Nested
  @DisplayName("clearance of the caller, as the manual create applies it")
  class CallerClearance {

    @Test
    @DisplayName("given_aCallerHoldingAmber_should_createUpToAmberAndSkipAboveWithBothOrders")
    void given_aCallerHoldingAmber_should_createUpToAmberAndSkipAboveWithBothOrders() {
      // Act
      RowImportResult result =
          run(
              List.of(tlpClear, tlpGreen, tlpAmber, tlpRed),
              List.of(tlpClear, tlpGreen, tlpAmber),
              row(1, "TLP", "TLP:CUSTOM3", "#ffffff", "3"),
              row(2, "TLP", "TLP:CUSTOM0", "#ffffff", "0"),
              row(3, "TLP", "TLP:CUSTOM4", "#ffffff", "4"));

      // Assert
      assertEquals(ImportRowStatus.CREATED, at(result, 1).status());
      assertEquals(ImportRowStatus.CREATED, at(result, 2).status());
      assertSkipped(
          at(result, 3),
          ImportRowReason.INSUFFICIENT_ACCESS,
          Map.of(PARAM_ORDER, "4", PARAM_HIGHEST_ORDER, "3"));
    }

    @Test
    @DisplayName("given_anExistingTypeTheCallerHoldsNothingOn_should_skipWithNoMarkingOfItsType")
    void given_anExistingTypeTheCallerHoldsNothingOn_should_skipWithNoMarkingOfItsType() {
      // Arrange
      MarkingDefinition papClear = marking("pap-clear", "PAP", "PAP:CLEAR", 1, "#ffffff");

      // Act
      RowImportResult result =
          run(
              List.of(tlpAmber, papClear),
              List.of(tlpAmber),
              row(1, "PAP", "PAP:NEW", "#ffffff", "0"));

      // Assert
      assertSkipped(at(result, 1), ImportRowReason.INSUFFICIENT_ACCESS, Map.of());
    }

    @Test
    @DisplayName("given_aCallerWithNoGrantAtAll_should_skipEveryRowOfAnExistingType")
    void given_aCallerWithNoGrantAtAll_should_skipEveryRowOfAnExistingType() {
      // Act
      RowImportResult result =
          run(
              List.of(tlpClear),
              List.of(),
              row(1, "TLP", "TLP:A", "#ffffff", "0"),
              row(2, "TLP", "TLP:B", "#ffffff", "1"));

      // Assert
      assertEquals(2, result.skipped());
      assertEquals(0, result.created());
    }

    @Test
    @DisplayName("given_aNewType_should_createItsFirstRowAndSkipTheNextOneOfThatType")
    void given_aNewType_should_createItsFirstRowAndSkipTheNextOneOfThatType() {
      // Act - the clearance is fixed at construction: the type created by the file widens nothing.
      RowImportResult result =
          run(
              List.of(tlpAmber),
              List.of(tlpAmber),
              row(1, "PAP", "PAP:RED", "#ff0000", "4"),
              row(2, "PAP", "PAP:CLEAR", "#ffffff", "1"));

      // Assert
      assertEquals(ImportRowStatus.CREATED, at(result, 1).status());
      assertSkipped(at(result, 2), ImportRowReason.INSUFFICIENT_ACCESS, Map.of());
    }

    @Test
    @DisplayName("given_aCallerHoldingEveryLevel_should_createEveryRow")
    void given_aCallerHoldingEveryLevel_should_createEveryRow() {
      // Act
      RowImportResult result =
          run(List.of(tlpClear, tlpRed), EVERY_LEVEL, row(1, "TLP", "TLP:ABOVE", "#ffffff", "99"));

      // Assert
      assertEquals(1, result.created());
    }

    @Test
    @DisplayName("given_aRowIdenticalToOrConflictingWithAnExistingMarking_should_keepThatOutcome")
    void given_aRowIdenticalToOrConflictingWithAnExistingMarking_should_keepThatOutcome() {
      // Act - re-importing an exported file must not flood a limited caller with access errors
      RowImportResult result =
          run(
              List.of(tlpClear, tlpRed),
              List.of(tlpClear),
              row(1, "TLP", "TLP:RED", "#ff0000", "4"),
              row(2, "TLP", "TLP:RED", "#00ff00", "4"));

      // Assert
      assertEquals(ImportRowStatus.IGNORED, at(result, 1).status());
      assertEquals(ImportRowReason.CONFLICT, at(result, 2).reason());
    }

    @Test
    @DisplayName("given_aSkippedRow_should_notRegisterItsKeyForALaterRow")
    void given_aSkippedRow_should_notRegisterItsKeyForALaterRow() {
      // Act
      RowImportResult result =
          run(
              List.of(tlpClear, tlpGreen),
              List.of(tlpClear, tlpGreen),
              row(1, "TLP", "TLP:CUSTOM", "#ffffff", "9"),
              row(2, "TLP", "TLP:CUSTOM", "#ffffff", "2"));

      // Assert
      assertEquals(ImportRowReason.INSUFFICIENT_ACCESS, at(result, 1).reason());
      assertEquals(ImportRowStatus.CREATED, at(result, 2).status());
    }
  }
}
