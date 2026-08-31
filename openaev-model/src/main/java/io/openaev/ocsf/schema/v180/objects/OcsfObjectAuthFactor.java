package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectAuthFactor extends OcsfObject {

  @com.fasterxml.jackson.annotation.JsonProperty(value = "device")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectDevice deviceField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "email_addr")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeEmailT emailAddrField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "factor_type")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT factorTypeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "factor_type_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT factorTypeIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_hotp")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT isHotpField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_totp")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT isTotpField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "phone_number")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT phoneNumberField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "provider")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT providerField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "security_questions")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT>
      securityQuestionsField;
}
