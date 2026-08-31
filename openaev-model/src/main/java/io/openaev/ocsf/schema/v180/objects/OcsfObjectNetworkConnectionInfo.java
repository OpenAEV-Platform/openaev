package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectNetworkConnectionInfo extends OcsfObject {

  @com.fasterxml.jackson.annotation.JsonProperty(value = "boundary")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT boundaryField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "boundary_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT boundaryIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "community_uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT communityUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "direction")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT directionField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "direction_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT directionIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "flag_history")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT flagHistoryField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "protocol_name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT protocolNameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "protocol_num")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT protocolNumField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "protocol_ver")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT protocolVerField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "protocol_ver_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT protocolVerIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "session")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectSession sessionField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "tcp_flags")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT tcpFlagsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;
}
