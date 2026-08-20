package io.openaev.ocsf.schema.v190.objects;

public class OcsfObjectDnsSection {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "tsig")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectTsig tsigField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "records")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectDnsResourceRecord recordsField;
}
