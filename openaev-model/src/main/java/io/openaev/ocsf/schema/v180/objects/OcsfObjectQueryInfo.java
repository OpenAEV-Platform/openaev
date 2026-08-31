package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectQueryInfo extends OcsfObject {

  @com.fasterxml.jackson.annotation.JsonProperty(value = "bytes")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeLongT bytesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "data")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeJsonT dataField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "query_string")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT queryStringField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "query_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT queryTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "query_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT queryTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;
}
