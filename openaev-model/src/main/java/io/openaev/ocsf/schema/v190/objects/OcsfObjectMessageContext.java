package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectMessageContext extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "ai_role")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT aiRoleField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "ai_role_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT aiRoleIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "application")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectApplication applicationField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "completion_tokens")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT completionTokensField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT nameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "prompt_text")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT promptTextField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "prompt_tokens")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT promptTokensField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "response_text")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT responseTextField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "service")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectService serviceField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "total_tokens")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT totalTokensField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_numeric")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT uidNumericField;
}
