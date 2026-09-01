package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectPolicy extends OcsfObject {
  /**
   * Additional data about the policy such as the underlying JSON policy itself or other details.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "data")
  @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
      using = io.openaev.ocsf.schema.v180.ObjectNodeDeserialiser.class)
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeJsonT dataField;

  /** The description of the policy. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "desc")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT descField;

  /** The policy group. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "group")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectGroup groupField;

  /** A determination if the content of a policy was applied to a target or request, or not. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_applied")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT isAppliedField;

  /** The policy name. For example: <code>AdministratorAccess Policy</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  /**
   * The policy type. For example: <code>
   * Identity Policy, Resource Policy, Service Control Policy, etc.</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT typeField;

  /** A unique identifier of the policy instance. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;

  /** The policy version number. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT versionField;
}
