package io.openaev.ocsf.schema.v190.objects;

public class OcsfObjectDigitalSignature {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT createdTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "algorithm_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT algorithmIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "state_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT stateIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT createdTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "state")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT stateField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "algorithm")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT algorithmField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "serialization_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT serializationIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "digest")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectFingerprint digestField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "certificate")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectCertificate certificateField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "developer_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT developerUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "serialization")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT serializationField;
}
