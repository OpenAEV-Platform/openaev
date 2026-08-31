package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectFingerprint extends OcsfObject {

  @com.fasterxml.jackson.annotation.JsonProperty(value = "algorithm")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT algorithmField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "algorithm_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT algorithmIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "encoding")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT encodingField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "encoding_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT encodingIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "serialization")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT serializationField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "serialization_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT serializationIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "value")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT valueField;
}
