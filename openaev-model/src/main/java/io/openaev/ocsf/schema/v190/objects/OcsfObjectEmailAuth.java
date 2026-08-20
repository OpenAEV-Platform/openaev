package io.openaev.ocsf.schema.v190.objects;

public class OcsfObjectEmailAuth {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "dmarc_override")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT dmarcOverrideField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "spf")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT spfField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "dkim_domain")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT dkimDomainField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "dkim_signature")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT dkimSignatureField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "dmarc")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT dmarcField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "dmarc_policy")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT dmarcPolicyField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "dkim")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT dkimField;
}
