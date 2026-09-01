package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectActor extends OcsfObject {
  /**
   * The client application or service that initiated the activity. This can be in conjunction with
   * the <code>user</code> if present. Note that <code>app_name</code> is distinct from the <code>
   * process</code> if present.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "app_name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT appNameField;

  /**
   * The unique identifier of the client application or service that initiated the activity. This
   * can be in conjunction with the <code>user</code> if present. Note that <code>app_name</code> is
   * distinct from the <code>process.pid</code> or <code>process.uid</code> if present.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "app_uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT appUidField;

  /**
   * Provides details about an authorization, such as authorization outcome, and any associated
   * policies related to the activity/event.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "authorizations")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectAuthorization>
      authorizationsField;

  /** This object describes details about the Identity Provider used. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "idp")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectIdp idpField;

  /** The name of the service that invoked the activity as described in the event. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "invoked_by")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT invokedByField;

  /** The process that initiated the activity. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "process")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectProcess processField;

  /** The user session from which the activity was initiated. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "session")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectSession sessionField;

  /**
   * The user that initiated the activity or the user context from which the activity was initiated.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "user")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectUser userField;
}
