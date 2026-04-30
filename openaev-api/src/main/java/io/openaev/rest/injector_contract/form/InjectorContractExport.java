package io.openaev.rest.injector_contract.form;

import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvBindByPosition;
import lombok.Data;

@Data
public class InjectorContractExport {

  @CsvBindByName(column = "type", required = true)
  @CsvBindByPosition(position = 0)
  private String type;

  @CsvBindByName(column = "name", required = true)
  @CsvBindByPosition(position = 1)
  private String name;

  @CsvBindByName(column = "domains", required = true)
  @CsvBindByPosition(position = 2)
  private String domains;

  @CsvBindByName(column = "platforms", required = true)
  @CsvBindByPosition(position = 3)
  private String platforms;

  @CsvBindByName(column = "status", required = true)
  @CsvBindByPosition(position = 4)
  private String status;

  @CsvBindByName(column = "tags", required = true)
  @CsvBindByPosition(position = 5)
  private String tags;

  @CsvBindByName(column = "attack_pattern", required = true)
  @CsvBindByPosition(position = 10)
  private String attackPattern;

  @CsvBindByName(column = "description", required = true)
  @CsvBindByPosition(position = 7)
  private String description;

  @CsvBindByName(column = "source", required = true)
  @CsvBindByPosition(position = 8)
  private String source;

  @CsvBindByName(column = "created_at", required = true)
  @CsvBindByPosition(position = 9)
  private String createdAt;

  @CsvBindByName(column = "updated_at", required = true)
  @CsvBindByPosition(position = 6)
  private String updatedAt;

  @CsvBindByName(column = "origin", required = true)
  @CsvBindByPosition(position = 11)
  private String origin;
}
