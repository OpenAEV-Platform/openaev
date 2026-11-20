package io.openaev.service.marking_definition;

import static com.opencsv.ICSVWriter.DEFAULT_SEPARATOR;

import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.ICSVParser;
import com.opencsv.exceptions.CsvValidationException;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.utils.CsvInjectionUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads the CSV of an imported marking file into {@link MarkingRow}s. Does no business validation
 * of a row: a bad row is the business of the import, only a file that cannot be read as a whole is
 * refused here, with a fixed message.
 */
public final class MarkingCsvParser {

  static final int MAX_ROWS = 1000;

  static final String COLUMN_TYPE = "type";
  static final String COLUMN_DEFINITION = "definition";
  static final String COLUMN_COLOR = "color";
  static final String COLUMN_ORDER = "order";

  private static final char BOM = '﻿';

  private MarkingCsvParser() {}

  public static List<MarkingRow> parse(InputStream content) {
    try (CSVReader reader =
        new CSVReaderBuilder(
                new InputStreamReader(
                    content,
                    StandardCharsets.UTF_8
                        .newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)))
            .withCSVParser(
                new CSVParserBuilder()
                    .withSeparator(DEFAULT_SEPARATOR)
                    // The export doubles a quote and has no other escape: a backslash is data.
                    .withEscapeChar(ICSVParser.NULL_CHARACTER)
                    .withIgnoreQuotations(false)
                    .build())
            .build()) {
      return readRows(reader);
    } catch (CharacterCodingException e) {
      throw new BadRequestException("The file is not encoded in UTF-8");
    } catch (CsvValidationException e) {
      throw new BadRequestException("The file is not a valid CSV file");
    } catch (IOException e) {
      // CsvMalformedLineException (an unbalanced quote) is one of them.
      throw new BadRequestException("The file could not be read as a CSV file");
    }
  }

  private static List<MarkingRow> readRows(CSVReader reader)
      throws IOException, CsvValidationException {
    String[] header = nextNonBlankLine(reader);
    if (header == null) {
      throw new BadRequestException("The file is empty");
    }
    Map<String, Integer> columns = indexColumns(header);

    List<MarkingRow> rows = new ArrayList<>();
    String[] line;
    while ((line = nextNonBlankLine(reader)) != null) {
      if (rows.size() == MAX_ROWS) {
        throw new BadRequestException("The file has too many rows");
      }
      rows.add(
          new MarkingRow(
              rows.size() + 1,
              cell(line, columns.get(COLUMN_TYPE), true),
              cell(line, columns.get(COLUMN_DEFINITION), true),
              cell(line, columns.get(COLUMN_COLOR), false),
              cell(line, columns.get(COLUMN_ORDER), false)));
    }
    if (rows.isEmpty()) {
      throw new BadRequestException("The file has a header but no row to import");
    }
    return rows;
  }

  private static String[] nextNonBlankLine(CSVReader reader)
      throws IOException, CsvValidationException {
    String[] line;
    while ((line = reader.readNext()) != null) {
      if (line.length > 1 || !line[0].isBlank()) {
        return line;
      }
    }
    return null;
  }

  private static Map<String, Integer> indexColumns(String[] header) {
    Map<String, Integer> columns = new HashMap<>();
    for (int index = 0; index < header.length; index++) {
      String name = header[index];
      if (index == 0 && !name.isEmpty() && name.charAt(0) == BOM) {
        name = name.substring(1);
      }
      columns.putIfAbsent(name.trim().toLowerCase(Locale.ROOT), index);
    }
    for (String required : List.of(COLUMN_TYPE, COLUMN_DEFINITION, COLUMN_COLOR, COLUMN_ORDER)) {
      if (!columns.containsKey(required)) {
        throw new BadRequestException(
            "A required column is missing: the file must have the columns type, definition, color"
                + " and order");
      }
    }
    return columns;
  }

  // A cell the row is too short to hold reads as empty. Only the free-text columns are exported
  // neutralised, the colour and the order are digits and hex.
  private static String cell(String[] line, int index, boolean neutralised) {
    String value = index < line.length ? line[index] : "";
    return (neutralised ? CsvInjectionUtils.undoNeutralisation(value) : value).trim();
  }
}
