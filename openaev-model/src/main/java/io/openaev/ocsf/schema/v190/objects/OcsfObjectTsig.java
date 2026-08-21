package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

public class OcsfObjectTsig extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "error_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT errorIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "algorithm")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT algorithmField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "error")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT errorField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "key_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT keyNameField;
}
