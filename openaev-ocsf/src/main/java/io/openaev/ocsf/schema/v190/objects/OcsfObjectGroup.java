package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectGroup extends OcsfObject {
  /** The group description. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "desc")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT descField;

  /** The domain where the group is defined. For example: the LDAP or Active Directory domain. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "domain")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT domainField;

  /** The group name. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT nameField;

  /** The group privileges. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "privileges")
  private java.util.List<io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT> privilegesField;

  /** The type of the group. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT typeField;

  /** The alternate unique identifier. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_alt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidAltField;

  /**
   * The unique identifier of the group. For example, for Windows events this is the security
   * identifier (SID) of the group. Another example, pool id or desktop id that the device belongs
   * to.
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
}
