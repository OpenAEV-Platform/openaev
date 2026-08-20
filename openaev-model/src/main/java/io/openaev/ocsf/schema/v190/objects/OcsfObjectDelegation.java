package io.openaev.ocsf.schema.v190.objects;

public class OcsfObjectDelegation {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT createdTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "parent_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT parentUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "issuer_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT issuerUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT createdTimeDtField;
}
