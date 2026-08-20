package io.openaev.ocsf.schema.v190.objects;

public class OcsfObjectQueryInfo {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT nameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "bytes")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT bytesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "data")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeJsonT dataField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "query_string")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT queryStringField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "query_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT queryTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_numeric")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT uidNumericField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "query_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT queryTimeDtField;
}
