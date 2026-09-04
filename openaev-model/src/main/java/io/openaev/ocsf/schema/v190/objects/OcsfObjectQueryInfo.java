package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectQueryInfo extends OcsfObject {
  /** The size of the data returned from the query. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "bytes")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT bytesField;

  /** The data returned from the query execution. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "data")
  @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
      using = io.openaev.ocsf.schema.v190.ObjectNodeDeserialiser.class)
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeJsonT dataField;

  /** The query name for a saved or scheduled query. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT nameField;

  /**
   * A string representing the query code being run. For example: <code>SELECT * FROM my_table
   * </code>
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "query_string")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT queryStringField;

  /** The time when the query was run. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "query_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT queryTimeDtField;

  /** The time when the query was run. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "query_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT queryTimeField;

  /** The unique identifier of the query. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;

  /**
   * The <code>uid</code> attribute in numeric form where applicable.<br>
   * <strong>Note:</strong> Producers may populate <code>uid_numeric</code> only in addition to
   * <code>uid</code> and not as an alternative to it.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_numeric")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT uidNumericField;
}
