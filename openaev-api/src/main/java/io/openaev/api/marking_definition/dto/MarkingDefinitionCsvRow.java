package io.openaev.api.marking_definition.dto;

import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvBindByPosition;
import lombok.Data;

/** A row of the marking definitions CSV file: the same shape is exported and imported. */
@Data
public class MarkingDefinitionCsvRow {

  @CsvBindByName(column = "type", required = true)
  @CsvBindByPosition(position = 0)
  private String type;

  @CsvBindByName(column = "definition", required = true)
  @CsvBindByPosition(position = 1)
  private String definition;

  @CsvBindByName(column = "color", required = true)
  @CsvBindByPosition(position = 2)
  private String color;

  @CsvBindByName(column = "order", required = true)
  @CsvBindByPosition(position = 3)
  private String order;
}
