package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectTls extends OcsfObject {
  /** The integer value of a TLS alert per the TLS specification. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "alert")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT alertField;

  /**
   * The Chain of Certificate Serial Numbers field provides a chain of Certificate Issuer Serial
   * Numbers leading to the Root Certificate Issuer.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "certificate_chain")
  private java.util.List<io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT>
      certificateChainField;

  /** The certificate object containing information about the digital certificate. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "certificate")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectCertificate certificateField;

  /** The negotiated cipher suite. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "cipher")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT cipherField;

  /** The client cipher suites that were exchanged during the TLS handshake negotiation. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "client_ciphers")
  private java.util.List<io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT>
      clientCiphersField;

  /** The list of TLS extensions. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "extension_list")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectTlsExtension>
      extensionListField;

  /**
   * The amount of total time for the TLS handshake to complete after the TCP connection is
   * established, including client-side delays, in milliseconds.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "handshake_dur")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT handshakeDurField;

  /** The MD5 hash of a JA3 string. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "ja3_hash")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectFingerprint ja3HashField;

  /** The MD5 hash of a JA3S string. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "ja3s_hash")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectFingerprint ja3sHashField;

  /** The length of the encryption key. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "key_length")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT keyLengthField;

  /** The list of subject alternative names that are secured by a specific certificate. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "sans")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectSan> sansField;

  /** The server cipher suites that were exchanged during the TLS handshake negotiation. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "server_ciphers")
  private java.util.List<io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT>
      serverCiphersField;

  /** The Server Name Indication (SNI) extension sent by the client. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "sni")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT sniField;

  /** The list of TLS extensions. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "tls_extension_list")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectTlsExtension>
      tlsExtensionListField;

  /** The TLS protocol version. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT versionField;
}
