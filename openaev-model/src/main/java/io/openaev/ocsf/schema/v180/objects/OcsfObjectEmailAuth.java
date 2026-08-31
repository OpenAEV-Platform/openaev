package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectEmailAuth extends OcsfObject {

  @com.fasterxml.jackson.annotation.JsonProperty(value = "dkim_domain")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT dkimDomainField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "dkim")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT dkimField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "dkim_signature")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT dkimSignatureField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "dmarc")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT dmarcField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "dmarc_override")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT dmarcOverrideField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "dmarc_policy")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT dmarcPolicyField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "spf")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT spfField;
}
