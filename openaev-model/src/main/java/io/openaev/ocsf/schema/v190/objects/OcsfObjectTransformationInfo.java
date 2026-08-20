package io.openaev.ocsf.schema.v190.objects;

public class OcsfObjectTransformationInfo {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "url_string")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeUrlT urlStringField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT nameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "product")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectProduct productField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT timeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT timeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "lang")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT langField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_numeric")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT uidNumericField;
}
