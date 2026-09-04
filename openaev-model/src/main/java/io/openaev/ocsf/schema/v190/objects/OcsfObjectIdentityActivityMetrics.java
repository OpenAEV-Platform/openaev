package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectIdentityActivityMetrics extends OcsfObject {
  /**
   * The timestamp when this identity was first observed or created in the system. This helps
   * establish the identity's age and lifecycle stage for risk assessment.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "first_seen_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT firstSeenTimeDtField;

  /**
   * The timestamp when this identity was first observed or created in the system. This helps
   * establish the identity's age and lifecycle stage for risk assessment.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "first_seen_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT firstSeenTimeField;

  /**
   * The timestamp when this identity last successfully authenticated to any system or service. This
   * differs from <code>last_seen_time</code> as it specifically tracks authentication events rather
   * than all activities.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_authentication_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT lastAuthenticationTimeDtField;

  /**
   * The timestamp when this identity last successfully authenticated to any system or service. This
   * differs from <code>last_seen_time</code> as it specifically tracks authentication events rather
   * than all activities.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_authentication_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT lastAuthenticationTimeField;

  /**
   * The timestamp of the most recent activity performed by this identity, including authentication,
   * resource access, or API calls. This is the most comprehensive indicator of identity usage
   * recency.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_seen_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT lastSeenTimeDtField;

  /**
   * The timestamp of the most recent activity performed by this identity, including authentication,
   * resource access, or API calls. This is the most comprehensive indicator of identity usage
   * recency.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_seen_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT lastSeenTimeField;

  /**
   * The timestamp when password-based authentication was last used by this identity. This helps
   * distinguish between password and other authentication methods (MFA, SSO, certificates) and
   * identify password-specific usage patterns.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "password_last_used_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT passwordLastUsedTimeDtField;

  /**
   * The timestamp when password-based authentication was last used by this identity. This helps
   * distinguish between password and other authentication methods (MFA, SSO, certificates) and
   * identify password-specific usage patterns.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "password_last_used_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT passwordLastUsedTimeField;

  /**
   * Details about the programmatic credentials associated with this identity, such as API keys,
   * service account keys, access tokens, and client certificates used for automated access.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "programmatic_credentials")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectProgrammaticCredential>
      programmaticCredentialsField;
}
