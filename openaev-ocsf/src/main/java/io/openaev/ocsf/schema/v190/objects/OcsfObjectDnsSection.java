package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectDnsSection extends OcsfObject {
  /** The resource records contained in this DNS section. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "records")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectDnsResourceRecord>
      recordsField;

  /**
   * The TSIG (Transaction Signature) record present in this DNS section, used to authenticate the
   * entire DNS message. Per RFC 2845, at most one TSIG record is permitted per DNS message.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "tsig")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectTsig tsigField;
}
