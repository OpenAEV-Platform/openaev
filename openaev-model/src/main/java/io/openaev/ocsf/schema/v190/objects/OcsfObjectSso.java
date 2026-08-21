package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

public class OcsfObjectSso extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT modifiedTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "idle_timeout")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT idleTimeoutField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "certificate")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectCertificate certificateField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT createdTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "login_endpoint")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeUrlT loginEndpointField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT createdTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "protocol_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT protocolNameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "duration_mins")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT durationMinsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "scopes")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT scopesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "vendor_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT vendorNameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT nameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "auth_protocol")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT authProtocolField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "metadata_endpoint")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeUrlT metadataEndpointField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "auth_protocol_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT authProtocolIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "logout_endpoint")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeUrlT logoutEndpointField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT modifiedTimeField;
}
