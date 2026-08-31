package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectProgrammaticCredential extends OcsfObject {

  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_used_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT lastUsedTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_used_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT lastUsedTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT typeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;
}
