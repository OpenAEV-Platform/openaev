package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectActor extends OcsfObject {
  /**
   * The client application or service that initiated the activity. This can be in conjunction with
   * the <code>user</code> if present. Note that <code>app_name</code> is distinct from the <code>
   * process</code> if present.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "app_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT appNameField;

  /**
   * The unique identifier of the client application or service that initiated the activity. This
   * can be in conjunction with the <code>user</code> if present. Note that <code>app_name</code> is
   * distinct from the <code>process.pid</code> or <code>process.uid</code> if present.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "app_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT appUidField;

  /**
   * The client application or service that initiated the activity. This can be in conjunction with
   * the <code>user</code> if present. Note that <code>application</code> is distinct from the
   * <code>process</code> if present.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "application")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectApplication applicationField;

  /**
   * Provides details about an authorization, such as authorization outcome, and any associated
   * policies related to the activity/event.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "authorizations")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectAuthorization>
      authorizationsField;

  /**
   * The actor's role, or as an alternative to <code>user</code> or <code>process</code> when the
   * role is serving as a security principal for the operation that initiated the activity.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "iam_role")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectIamRole iamRoleField;

  /** This object describes details about the Identity Provider used. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "idp")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectIdp idpField;

  /** The name of the service that invoked the activity as described in the event. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "invoked_by")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT invokedByField;

  /** The process that initiated the activity. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "process")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectProcess processField;

  /** The user session from which the activity was initiated. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "session")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectSession sessionField;

  /**
   * The user that initiated the activity or the user context from which the activity was initiated.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "user")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectUser userField;
}
