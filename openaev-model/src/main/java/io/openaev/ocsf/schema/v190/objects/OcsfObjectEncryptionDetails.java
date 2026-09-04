package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectEncryptionDetails extends OcsfObject {
  /** The encryption algorithm used, normalized to the caption of 'algorithm_id */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "algorithm")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT algorithmField;

  /** The encryption algorithm used. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "algorithm_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT algorithmIdField;

  /** The length of the encryption key used. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "key_length")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT keyLengthField;

  /** The unique identifier of the key used for encryption. For example, AWS KMS Key ARN. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "key_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT keyUidField;

  /** The type of the encryption used. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT typeField;
}
