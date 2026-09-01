package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectOccurrenceDetails extends OcsfObject {
  /** The cell name/reference in a spreadsheet. e.g <code>A2</code> */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "cell_name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT cellNameField;

  /** The column name in a spreadsheet, where the information was discovered. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "column_name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT columnNameField;

  /**
   * The column number in a spreadsheet or a plain text document, where the information was
   * discovered.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "column_number")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT columnNumberField;

  /** The line number of the last line of the file, where the information was discovered. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "end_line")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT endLineField;

  /** The JSON path of the attribute in a json record, where the information was discovered */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "json_path")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT jsonPathField;

  /** The page number in a document, where the information was discovered. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "page_number")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT pageNumberField;

  /**
   * The index of the record in the array of records, where the information was discovered. e.g. the
   * index of a record in an array of JSON records in a file.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "record_index_in_array")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT recordIndexInArrayField;

  /** The row number in a spreadsheet, where the information was discovered. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "row_number")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT rowNumberField;

  /** The line number of the first line of the file, where the information was discovered. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "start_line")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT startLineField;
}
