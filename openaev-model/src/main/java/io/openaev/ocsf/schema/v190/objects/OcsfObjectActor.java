package io.openaev.ocsf.schema.v190.objects;

public class OcsfObjectActor {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "session")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectSession sessionField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "user")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectUser userField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "authorizations")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectAuthorization authorizationsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "app_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT appNameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "process")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectProcess processField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "application")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectApplication applicationField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "app_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT appUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "iam_role")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectIamRole iamRoleField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "invoked_by")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT invokedByField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "idp")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectIdp idpField;
}
