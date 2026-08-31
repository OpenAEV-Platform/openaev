package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectIdentityActivityMetrics extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "first_seen_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT firstSeenTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "first_seen_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT firstSeenTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_authentication_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT lastAuthenticationTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_authentication_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT lastAuthenticationTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_seen_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT lastSeenTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_seen_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT lastSeenTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "password_last_used_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT passwordLastUsedTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "password_last_used_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT passwordLastUsedTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "programmatic_credentials")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectProgrammaticCredential>
      programmaticCredentialsField;
}
