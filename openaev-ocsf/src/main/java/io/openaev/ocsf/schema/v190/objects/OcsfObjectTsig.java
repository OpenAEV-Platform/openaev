package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectTsig extends OcsfObject {
  /** The HMAC algorithm used to compute the MAC. For example: <code>hmac-sha256</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "algorithm")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT algorithmField;

  /**
   * The TSIG error, normalized to the caption of the <code>error_id</code> value. In the case of
   * 'Other', it is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "error")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT errorField;

  /**
   * The normalized TSIG-specific error code as defined in RFC 2845 and RFC 6895. This is
   * independent of the DNS message header RCODE.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "error_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT errorIdField;

  /**
   * The name of the shared secret key used to sign the DNS message. For example: <code>
   * my-tsig-key.example.com.</code>
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "key_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT keyNameField;
}
