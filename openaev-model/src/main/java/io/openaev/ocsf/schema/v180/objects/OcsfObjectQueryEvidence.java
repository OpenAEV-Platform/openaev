package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectQueryEvidence extends OcsfObject {
  /** The network connection information related to a Network Connection query type. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "connection_info")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectNetworkConnectionInfo connectionInfoField;

  /** The file that is the target of the query when query_type_id indicates a File query. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "file")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectFile fileField;

  /** The folder that is the target of the query when query_type_id indicates a Folder query. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "folder")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectFile folderField;

  /**
   * The administrative group that is the target of the query when query_type_id indicates an Admin
   * Group query.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "group")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectGroup groupField;

  /** The job object that pertains to the event when query_type_id indicates a Job query. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "job")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectJob jobField;

  /** The kernel object that pertains to the event when query_type_id indicates a Kernel query. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "kernel")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectKernel kernelField;

  /** The module that pertains to the event when query_type_id indicates a Module query. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "module")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectModule moduleField;

  /**
   * The physical or virtual network interfaces that are associated with the device when
   * query_type_id indicates a Network Interfaces query.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "network_interfaces")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectNetworkInterface>
      networkInterfacesField;

  /**
   * The peripheral device that triggered the event when query_type_id indicates a Peripheral Device
   * query.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "peripheral_device")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectPeripheralDevice peripheralDeviceField;

  /** The process that pertains to the event when query_type_id indicates a Process query. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "process")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectProcess processField;

  /** The normalized caption of query_type_id or the source-specific query type. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "query_type")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT queryTypeField;

  /** The normalized type of system query performed against a device or system component. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "query_type_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT queryTypeIdField;

  /** The registry key object describes a Windows registry key. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "reg_key")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectRegKey regKeyField;

  /** The registry key object describes a Windows registry value. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "reg_value")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectRegValue regValueField;

  /** The service that pertains to the event when query_type_id indicates a Service query. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "service")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectService serviceField;

  /** The authenticated user or service session when query_type_id indicates a Session query. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "session")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectSession sessionField;

  /**
   * The startup item object that pertains to the event when query_type_id indicates a Startup Item
   * query.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "startup_item")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectStartupItem startupItemField;

  /**
   * The state of the socket, normalized to the caption of the state_id value. In the case of
   * 'Other', it is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "state")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT stateField;

  /** The state of the TCP socket for the network connection. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "tcp_state_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT tcpStateIdField;

  /** The user that pertains to the event when query_type_id indicates a User query. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "user")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectUser userField;

  /**
   * The users that belong to the administrative group when query_type_id indicates a Users query.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "users")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectUser> usersField;
}
