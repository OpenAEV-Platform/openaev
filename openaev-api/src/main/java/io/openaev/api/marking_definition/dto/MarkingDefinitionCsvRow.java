package io.openaev.api.marking_definition.dto;

import static io.openaev.service.marking_definition.MarkingCsvParser.COLUMN_COLOR;
import static io.openaev.service.marking_definition.MarkingCsvParser.COLUMN_DEFINITION;
import static io.openaev.service.marking_definition.MarkingCsvParser.COLUMN_ORDER;
import static io.openaev.service.marking_definition.MarkingCsvParser.COLUMN_TYPE;

import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvBindByPosition;
import lombok.Data;

/**
 * A row of the marking definitions CSV file: the same shape is exported and imported. The column
 * names are the ones the parser reads back, so renaming one cannot break the re-import of an
 * exported file.
 */
@Data
public class MarkingDefinitionCsvRow {

  @CsvBindByName(column = COLUMN_TYPE, required = true)
  @CsvBindByPosition(position = 0)
  private String type;

  @CsvBindByName(column = COLUMN_DEFINITION, required = true)
  @CsvBindByPosition(position = 1)
  private String definition;

  @CsvBindByName(column = COLUMN_COLOR, required = true)
  @CsvBindByPosition(position = 2)
  private String color;

  @CsvBindByName(column = COLUMN_ORDER, required = true)
  @CsvBindByPosition(position = 3)
  private String order;
}
