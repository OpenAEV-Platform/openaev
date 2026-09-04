package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectLdapPerson extends OcsfObject {
  /** The cost center associated with the user. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "cost_center")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT costCenterField;

  /** The timestamp when the user was created. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT createdTimeDtField;

  /** The timestamp when the user was created. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT createdTimeField;

  /**
   * The timestamp when the user was deleted. In Active Directory (AD), when a user is deleted they
   * are moved to a temporary container and then removed after 30 days. So, this field can be
   * populated even after a user is deleted for the next 30 days.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "deleted_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT deletedTimeDtField;

  /**
   * The timestamp when the user was deleted. In Active Directory (AD), when a user is deleted they
   * are moved to a temporary container and then removed after 30 days. So, this field can be
   * populated even after a user is deleted for the next 30 days.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "deleted_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT deletedTimeField;

  /** The name of the department in which the user works. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "department")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT departmentField;

  /**
   * The display name of the LDAP person. According to RFC 2798, this is the preferred name of a
   * person to be used when displaying entries.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "display_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT displayNameField;

  /** A list of additional email addresses for the user. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "email_addrs")
  private java.util.List<io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeEmailT> emailAddrsField;

  /** The employee identifier assigned to the user by the organization. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "employee_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT employeeUidField;

  /** The given or first name of the user. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "given_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT givenNameField;

  /** The timestamp when the user was or will be hired by the organization. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "hire_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT hireTimeDtField;

  /** The timestamp when the user was or will be hired by the organization. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "hire_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT hireTimeField;

  /** The user's job title. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "job_title")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT jobTitleField;

  /**
   * The labels associated with the user. For example in AD this could be the <code>userType</code>,
   * <code>employeeType</code>. For example: <code>Member, Employee</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "labels")
  private java.util.List<io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT> labelsField;

  /** The last time when the user logged in. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_login_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT lastLoginTimeDtField;

  /** The last time when the user logged in. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_login_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT lastLoginTimeField;

  /**
   * The LDAP and X.500 <code>commonName</code> attribute, typically the full name of the person.
   * For example, <code>John Doe</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "ldap_cn")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT ldapCnField;

  /**
   * The X.500 Distinguished Name (DN) is a structured string that uniquely identifies an entry,
   * such as a user, in an X.500 directory service For example, <code>
   * cn=John Doe,ou=People,dc=example,dc=com</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "ldap_dn")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT ldapDnField;

  /** The timestamp when the user left or will be leaving the organization. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "leave_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT leaveTimeDtField;

  /** The timestamp when the user left or will be leaving the organization. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "leave_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT leaveTimeField;

  /**
   * The geographical location associated with a user. This is typically the user's usual work
   * location.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "location")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectLocation locationField;

  /**
   * The user's manager. This helps in understanding an org hierarchy. This should only ever be
   * populated once in an event. I.e. there should not be a manager's manager in an event.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "manager")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectUser managerField;

  /** The timestamp when the user entry was last modified. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT modifiedTimeDtField;

  /** The timestamp when the user entry was last modified. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT modifiedTimeField;

  /**
   * The primary office location associated with the user. This could be any string and isn't a
   * specific address. For example, <code>South East Virtual</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "office_location")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT officeLocationField;

  /** The telephone number of the user. Corresponds to the LDAP <code>Telephone-Number</code> CN. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "phone_number")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT phoneNumberField;

  /** The last or family name for the user. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "surname")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT surnameField;

  /** The list of tags; <code>{key:value}</code> pairs associated to the user. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "tags")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectKeyValueObject> tagsField;
}
