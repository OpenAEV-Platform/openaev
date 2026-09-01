package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectAuthFactor extends OcsfObject {
  /** Device used to complete an authentication request. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "device")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectDevice deviceField;

  /** The email address used in an email-based authentication factor. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "email_addr")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeEmailT emailAddrField;

  /** The type of authentication factor used in an authentication attempt. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "factor_type")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT factorTypeField;

  /** The normalized identifier for the authentication factor. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "factor_type_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT factorTypeIdField;

  /** Whether the authentication factor is an HMAC-based One-time Password (HOTP). */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_hotp")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT isHotpField;

  /** Whether the authentication factor is a Time-based One-time Password (TOTP). */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_totp")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT isTotpField;

  /** The phone number used for a telephony-based authentication request. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "phone_number")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT phoneNumberField;

  /** The name of provider for an authentication factor. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "provider")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT providerField;

  /** The question(s) provided to user for a question-based authentication factor. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "security_questions")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT>
      securityQuestionsField;
}
