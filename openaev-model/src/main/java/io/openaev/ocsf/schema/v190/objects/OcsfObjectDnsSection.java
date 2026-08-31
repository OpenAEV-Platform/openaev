package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectDnsSection extends OcsfObject {

  @com.fasterxml.jackson.annotation.JsonProperty(value = "records")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectDnsResourceRecord>
      recordsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "tsig")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectTsig tsigField;
}
