package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

public class OcsfObjectIamRole extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "resources")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectResourceDetails resourcesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "account")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectAccount accountField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "privileges")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT privilegesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "programmatic_credentials")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectProgrammaticCredential
      programmaticCredentialsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "session")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectSession sessionField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "policies")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectPolicy policiesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_alt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidAltField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT nameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_numeric")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT uidNumericField;
}
