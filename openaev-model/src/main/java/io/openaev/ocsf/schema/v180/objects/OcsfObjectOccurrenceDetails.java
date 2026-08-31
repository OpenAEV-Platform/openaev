package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectOccurrenceDetails extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "cell_name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT cellNameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "column_name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT columnNameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "column_number")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT columnNumberField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "end_line")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT endLineField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "json_path")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT jsonPathField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "page_number")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT pageNumberField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "record_index_in_array")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT recordIndexInArrayField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "row_number")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT rowNumberField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "start_line")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT startLineField;
}
