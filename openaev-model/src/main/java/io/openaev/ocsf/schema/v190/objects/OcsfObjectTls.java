package io.openaev.ocsf.schema.v190.objects;

public class OcsfObjectTls {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "ja3_hash")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectFingerprint ja3HashField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT versionField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "cipher")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT cipherField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "ja3s_hash")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectFingerprint ja3sHashField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "alert")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT alertField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "extension_list")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectTlsExtension extensionListField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "tls_extension_list")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectTlsExtension tlsExtensionListField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "key_length")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT keyLengthField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "certificate_chain")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT certificateChainField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "client_ciphers")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT clientCiphersField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "handshake_dur")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT handshakeDurField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "certificate")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectCertificate certificateField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "sans")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectSan sansField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "sni")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT sniField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "server_ciphers")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT serverCiphersField;
}
