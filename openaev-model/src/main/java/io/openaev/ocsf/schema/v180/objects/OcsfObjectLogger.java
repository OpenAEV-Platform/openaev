package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectLogger extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "device")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectDevice deviceField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "event_uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT eventUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_truncated")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT isTruncatedField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "log_format")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT logFormatField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "log_level")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT logLevelField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "log_name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT logNameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "log_provider")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT logProviderField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "log_version")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT logVersionField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "logged_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT loggedTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "logged_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT loggedTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "product")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectProduct productField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "transmit_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT transmitTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "transmit_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT transmitTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "untruncated_size")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT untruncatedSizeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT versionField;
}
