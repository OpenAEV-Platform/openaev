package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectProgrammaticCredential extends OcsfObject {
  /**
   * The timestamp when this programmatic credential was last used for authentication or API access.
   * This helps track credential usage patterns, identify dormant credentials that may pose security
   * risks, and support credential lifecycle management. The timestamp should reflect the most
   * recent successful authentication or API call using this credential.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_used_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT lastUsedTimeDtField;

  /**
   * The timestamp when this programmatic credential was last used for authentication or API access.
   * This helps track credential usage patterns, identify dormant credentials that may pose security
   * risks, and support credential lifecycle management. The timestamp should reflect the most
   * recent successful authentication or API call using this credential.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_used_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT lastUsedTimeField;

  /**
   * The type or category of programmatic credential, normalized to the caption of the type_id
   * value. In the case of 'Other', it is defined by the event source. Examples include 'API Key',
   * 'Service Account Key', 'Access Token', 'Client Certificate', 'OAuth Token', 'Personal Access
   * Token', etc.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT typeField;

  /**
   * The unique identifier of the programmatic credential. This could be an API key ID, service
   * account key ID, access token identifier, certificate serial number, or other unique identifier
   * that distinguishes this credential from others. Examples: AWS Access Key ID, GCP Service
   * Account Key ID, Azure Application ID, or OAuth2 token identifier.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;
}
