package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectQueryEvidence extends OcsfObject {

  @com.fasterxml.jackson.annotation.JsonProperty(value = "connection_info")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectNetworkConnectionInfo connectionInfoField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "file")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectFile fileField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "folder")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectFile folderField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "group")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectGroup groupField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "job")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectJob jobField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "kernel")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectKernel kernelField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "module")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectModule moduleField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "network_interfaces")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectNetworkInterface>
      networkInterfacesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "peripheral_device")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectPeripheralDevice peripheralDeviceField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "process")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectProcess processField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "query_type")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT queryTypeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "query_type_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT queryTypeIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "reg_key")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectRegKey regKeyField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "reg_value")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectRegValue regValueField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "service")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectService serviceField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "session")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectSession sessionField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "startup_item")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectStartupItem startupItemField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "state")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT stateField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "tcp_state_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT tcpStateIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "user")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectUser userField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "users")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectUser> usersField;
}
