package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectPortInfo extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "port")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypePortT portField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "protocol_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT protocolNameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "protocol_num")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT protocolNumField;
}
