package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectLdapPerson extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "cost_center")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT costCenterField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT createdTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT createdTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "deleted_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT deletedTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "deleted_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT deletedTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "department")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT departmentField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "display_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT displayNameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "email_addrs")
  private java.util.List<io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeEmailT> emailAddrsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "employee_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT employeeUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "given_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT givenNameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "hire_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT hireTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "hire_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT hireTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "job_title")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT jobTitleField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "labels")
  private java.util.List<io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT> labelsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_login_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT lastLoginTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_login_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT lastLoginTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "ldap_cn")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT ldapCnField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "ldap_dn")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT ldapDnField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "leave_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT leaveTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "leave_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT leaveTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "location")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectLocation locationField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "manager")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectUser managerField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT modifiedTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT modifiedTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "office_location")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT officeLocationField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "phone_number")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT phoneNumberField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "surname")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT surnameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "tags")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectKeyValueObject> tagsField;
}
