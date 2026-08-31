package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectDomainContact extends OcsfObject {

  @com.fasterxml.jackson.annotation.JsonProperty(value = "email_addr")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeEmailT emailAddrField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "location")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectLocation locationField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "phone_number")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT phoneNumberField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT typeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT typeIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;
}
