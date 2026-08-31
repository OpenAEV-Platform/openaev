package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectNetworkInterface extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "hostname")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeHostnameT hostnameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "ip")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIpT ipField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "mac")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeMacT macField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "namespace")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT namespaceField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "open_ports")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectPortInfo> openPortsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "subnet_prefix")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT subnetPrefixField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT typeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT typeIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;
}
