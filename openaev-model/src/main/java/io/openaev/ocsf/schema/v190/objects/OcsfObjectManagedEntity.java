package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectManagedEntity extends OcsfObject {
  /** The managed entity content as a JSON object. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "data")
  @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
      using = io.openaev.ocsf.schema.v190.ObjectNodeDeserialiser.class)
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeJsonT dataField;

  /** An addressable device, computer system or host. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "device")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectDevice deviceField;

  /** The email object. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "email")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectEmail emailField;

  /** The group object associated with an entity such as user, policy, or rule. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "group")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectGroup groupField;

  /** The detailed geographical location usually associated with an IP address. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "location")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectLocation locationField;

  /**
   * The name of the managed entity. It should match the name of the specific entity object's name
   * if populated, or the name of the managed entity if the <code>type_id</code> is 'Other'.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT nameField;

  /**
   * The Organization object containing details about the managed organizational entity. This object
   * includes properties such as the organization name, unique identifier, type, and other
   * organizational metadata. This attribute should be populated when <code>type_id</code> is <code>
   * 4</code> (Organization).
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "org")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectOrganization orgField;

  /** Describes details of a managed policy. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "policy")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectPolicy policyField;

  /**
   * The managed entity type. For example: <code>Policy</code>, <code>User</code>, <code>
   * Organization</code>, <code>Device</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT typeField;

  /**
   * The type of the Managed Entity. It is recommended to also populate the <code>type</code>
   * attribute with the associated label, or the source specific name if <code>Other</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT typeIdField;

  /**
   * The identifier of the managed entity. It should match the <code>uid</code> of the specific
   * entity's object UID if populated, or the source specific ID if the <code>type_id</code> is
   * 'Other'.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;

  /**
   * The <code>uid</code> attribute in numeric form where applicable.<br>
   * <strong>Note:</strong> Producers may populate <code>uid_numeric</code> only in addition to
   * <code>uid</code> and not as an alternative to it.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_numeric")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT uidNumericField;

  /** The user that pertains to the event or object. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "user")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectUser userField;

  /** The version of the managed entity. For example: <code>1.2.3</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT versionField;
}
