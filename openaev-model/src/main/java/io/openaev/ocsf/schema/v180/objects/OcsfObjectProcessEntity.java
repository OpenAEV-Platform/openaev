package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectProcessEntity extends OcsfObject {

  @com.fasterxml.jackson.annotation.JsonProperty(value = "cmd_line")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT cmdLineField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "cpid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeUuidT cpidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT createdTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT createdTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "path")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT pathField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "pid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT pidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;
}
