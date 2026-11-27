package io.openaev.service.marking_definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.openaev.rest.exception.BadRequestException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("MarkingCsvParser: reads the file, validates nothing of a row")
class MarkingCsvParserTest {

  private static final String HEADER = "type,definition,color,order\n";

  private static List<MarkingRow> parse(String content) {
    return MarkingCsvParser.parse(
        new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
  }

  @Nested
  @DisplayName("reading rows")
  class Reading {

    @Test
    @DisplayName("given_aValidFile_should_giveOneRowPerLineWithItsPosition")
    void given_aValidFile_should_giveOneRowPerLineWithItsPosition() {
      // Arrange
      String csv = HEADER + "TLP,TLP:AMBER,#ffc107,3\nPAP,PAP:RED,#ff0000,4\n";

      // Act
      List<MarkingRow> rows = parse(csv);

      // Assert
      assertEquals(
          List.of(
              new MarkingRow(1, "TLP", "TLP:AMBER", "#ffc107", "3"),
              new MarkingRow(2, "PAP", "PAP:RED", "#ff0000", "4")),
          rows);
    }

    @Test
    @DisplayName("given_reorderedExtraAndDifferentlyCasedColumns_should_readByHeaderName")
    void given_reorderedExtraAndDifferentlyCasedColumns_should_readByHeaderName() {
      // Arrange
      String csv = " ORDER ,Color,notes,Definition,TYPE\n3,#ffc107,ignored,TLP:AMBER,TLP\n";

      // Act
      List<MarkingRow> rows = parse(csv);

      // Assert
      assertEquals(List.of(new MarkingRow(1, "TLP", "TLP:AMBER", "#ffc107", "3")), rows);
    }

    @Test
    @DisplayName("given_aBom_should_stripItFromTheFirstColumnName")
    void given_aBom_should_stripItFromTheFirstColumnName() {
      // Arrange
      String csv = "﻿" + HEADER + "TLP,TLP:AMBER,#ffc107,3\n";

      // Act
      List<MarkingRow> rows = parse(csv);

      // Assert
      assertEquals(1, rows.size());
      assertEquals("TLP", rows.get(0).type());
    }

    @Test
    @DisplayName("given_shortAndLongRows_should_readMissingCellsAsEmptyAndIgnoreExtraOnes")
    void given_shortAndLongRows_should_readMissingCellsAsEmptyAndIgnoreExtraOnes() {
      // Arrange
      String csv = HEADER + "TLP,TLP:AMBER\nTLP,TLP:RED,#ff0000,4,extra,more\n";

      // Act
      List<MarkingRow> rows = parse(csv);

      // Assert
      assertEquals(new MarkingRow(1, "TLP", "TLP:AMBER", "", ""), rows.get(0));
      assertEquals(new MarkingRow(2, "TLP", "TLP:RED", "#ff0000", "4"), rows.get(1));
    }

    @Test
    @DisplayName("given_blankLines_should_skipThemWithoutCountingThemAsRows")
    void given_blankLines_should_skipThemWithoutCountingThemAsRows() {
      // Arrange
      String csv = "\n" + HEADER + "\nTLP,TLP:AMBER,#ffc107,3\n  \n\nPAP,PAP:RED,#ff0000,4\n";

      // Act
      List<MarkingRow> rows = parse(csv);

      // Assert
      assertEquals(List.of(1, 2), rows.stream().map(MarkingRow::position).toList());
    }

    @Test
    @DisplayName("given_quotedCellsWithSeparatorAndLineBreak_should_keepThemInOneCell")
    void given_quotedCellsWithSeparatorAndLineBreak_should_keepThemInOneCell() {
      // Arrange
      String csv = HEADER + "TLP,\"TLP:A,\n\"\"B\"\"\",#ffc107,3\n";

      // Act
      List<MarkingRow> rows = parse(csv);

      // Assert
      assertEquals("TLP:A,\n\"B\"", rows.get(0).definition());
    }

    @Test
    @DisplayName("given_accents_should_readThemAsUtf8")
    void given_accents_should_readThemAsUtf8() {
      // Arrange
      String csv = HEADER + "CONFIDENTIEL,SECRET DÉFENSE,#ffc107,3\n";

      // Act
      List<MarkingRow> rows = parse(csv);

      // Assert
      assertEquals("SECRET DÉFENSE", rows.get(0).definition());
    }

    @ParameterizedTest(name = "{0} is read back without the leading '")
    @ValueSource(strings = {"'=A", "'+A", "'-A", "'@A", "'\tA", "'\rA"})
    @DisplayName("given_aNeutralisedValue_should_takeTheLeadingQuoteOff")
    void given_aNeutralisedValue_should_takeTheLeadingQuoteOff(String neutralised) {
      // Arrange
      String csv = HEADER + "\"" + neutralised + "\",\"" + neutralised + "\",#ffc107,3\n";

      // Act
      MarkingRow row = parse(csv).get(0);

      // Assert
      assertEquals(neutralised.substring(1).trim(), row.type());
      assertEquals(neutralised.substring(1).trim(), row.definition());
    }

    @Test
    @DisplayName("given_aQuoteNotInFrontOfAFormulaStart_should_keepIt")
    void given_aQuoteNotInFrontOfAFormulaStart_should_keepIt() {
      // Arrange
      String csv = HEADER + "'TLP,O'NEIL,#ffc107,3\n";

      // Act
      MarkingRow row = parse(csv).get(0);

      // Assert
      assertEquals("'TLP", row.type());
      assertEquals("O'NEIL", row.definition());
    }

    @Test
    @DisplayName("given_backslashesAndEscapedQuotes_should_readThemAsExportedWithoutAlteringThem")
    void given_backslashesAndEscapedQuotes_should_readThemAsExportedWithoutAlteringThem() {
      // Arrange - the export quotes every cell and doubles a quote, it has no backslash escape
      String csv = HEADER + "\"A\\B\",\"C\\\"\"D\",#ffc107,3\n";

      // Act
      MarkingRow row = parse(csv).get(0);

      // Assert
      assertEquals("A\\B", row.type());
      assertEquals("C\\\"D", row.definition());
    }

    @Test
    @DisplayName("given_exactlyTheRowLimit_should_readThemAll")
    void given_exactlyTheRowLimit_should_readThemAll() {
      // Arrange
      String csv = HEADER + rows(MarkingCsvParser.MAX_ROWS);

      // Act
      List<MarkingRow> rows = parse(csv);

      // Assert
      assertEquals(MarkingCsvParser.MAX_ROWS, rows.size());
    }
  }

  @Nested
  @DisplayName("refusing the whole file, with a fixed message")
  class Refusing {

    private void assertRefused(String csv, String expectedMessagePart) {
      BadRequestException refused = assertThrows(BadRequestException.class, () -> parse(csv));
      assertEquals(true, refused.getMessage().contains(expectedMessagePart), refused.getMessage());
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"", "\n\n  \n"})
    @DisplayName("given_noContent_should_refuseAnEmptyFile")
    void given_noContent_should_refuseAnEmptyFile(String csv) {
      // Act / Assert
      assertRefused(csv, "empty");
    }

    @Test
    @DisplayName("given_aHeaderOnly_should_refuseNothingToImport")
    void given_aHeaderOnly_should_refuseNothingToImport() {
      // Act / Assert
      assertRefused(HEADER, "no row to import");
    }

    @ParameterizedTest(name = "missing from {0}")
    @ValueSource(
        strings = {
          "definition,color,order",
          "type,color,order",
          "type,definition,order",
          "type,definition,color",
          // Another separator reads the whole header as one column, which has none of the names.
          "type;definition;color;order"
        })
    @DisplayName("given_aRequiredColumnMissing_should_refuseTheFile")
    void given_aRequiredColumnMissing_should_refuseTheFile(String header) {
      // Act / Assert
      assertRefused(header + "\nTLP,TLP:AMBER,#ffc107,3\n", "required column is missing");
    }

    @Test
    @DisplayName("given_anUnbalancedQuote_should_refuseAMalformedFile")
    void given_anUnbalancedQuote_should_refuseAMalformedFile() {
      // Act / Assert
      assertRefused(HEADER + "TLP,\"TLP:AMBER,#ffc107,3\n", "CSV");
    }

    @Test
    @DisplayName("given_moreRowsThanTheLimit_should_refuseTheFile")
    void given_moreRowsThanTheLimit_should_refuseTheFile() {
      // Act / Assert
      assertRefused(HEADER + rows(MarkingCsvParser.MAX_ROWS + 1), "too many rows");
    }

    @Test
    @DisplayName("given_bytesThatAreNotUtf8_should_refuseTheFile")
    void given_bytesThatAreNotUtf8_should_refuseTheFile() {
      // Arrange
      byte[] latin1 =
          (HEADER + "TLP,SECRET DÉFENSE,#ffc107,3\n").getBytes(StandardCharsets.ISO_8859_1);

      // Act
      BadRequestException refused =
          assertThrows(
              BadRequestException.class,
              () -> MarkingCsvParser.parse(new ByteArrayInputStream(latin1)));

      // Assert
      assertEquals("The file is not encoded in UTF-8", refused.getMessage());
    }
  }

  private static String rows(int count) {
    return IntStream.rangeClosed(1, count)
        .mapToObj(index -> "TLP,TLP:" + index + ",#ffc107,3\n")
        .collect(Collectors.joining());
  }
}
