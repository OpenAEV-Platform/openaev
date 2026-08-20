package io.openaev.ocsf.schema.v190.classes;

public class OcsfClassSmbActivity {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "packet_list")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectPacket packetListField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "proxy_tls")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectTls proxyTlsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "confidence")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT confidenceField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "unmapped")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectObject unmappedField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "dialect")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT dialectField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "app_protocol_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT appProtocolNameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "dce_rpc")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectDceRpc dceRpcField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "open_type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT openTypeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "end_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT endTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT typeUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "ai_agent")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectAiAgent aiAgentField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "risk_level")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT riskLevelField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "authorizations")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectAuthorization authorizationsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "share_type_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT shareTypeIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "status")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT statusField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "status_detail")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT statusDetailField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "metadata")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectMetadata metadataField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "activity_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT activityNameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "risk_score")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT riskScoreField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "enrichments")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectEnrichment enrichmentsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT timeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "delegation")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectDelegation delegationField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "cumulative_traffic")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectNetworkTraffic cumulativeTrafficField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "proxy_connection_info")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectNetworkConnectionInfo
      proxyConnectionInfoField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "class_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT classNameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "raw_data")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT rawDataField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "policy")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectPolicy policyField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "command")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT commandField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "proxy_traffic")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectNetworkTraffic proxyTrafficField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "response")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectResponse responseField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "action_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT actionIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "confidence_score")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT confidenceScoreField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "raw_data_hash")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectFingerprint rawDataHashField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "tree_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT treeUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "observation_point")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT observationPointField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "proxy_http_request")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectHttpRequest proxyHttpRequestField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "malware_scan_info")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectMalwareScanInfo malwareScanInfoField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "message")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT messageField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "ai_model")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectAiModel aiModelField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "firewall_rule")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectFirewallRule firewallRuleField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT typeNameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT timeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "proxy")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectNetworkProxy proxyField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "start_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT startTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "category_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT categoryUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "status_code")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT statusCodeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "status_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT statusIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "traffic")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectNetworkTraffic trafficField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "timezone_offset")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT timezoneOffsetField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "ja4_fingerprint_list")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectJa4Fingerprint ja4FingerprintListField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "observation_point_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT observationPointIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "network_observation_point")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectNetworkEndpoint
      networkObservationPointField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "cloud")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectCloud cloudField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "attacks")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectAttack attacksField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "observables")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectObservable observablesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "api")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectApi apiField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "osint")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectOsint osintField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "attestation_list")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectAttestation attestationListField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "action")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT actionField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "category_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT categoryNameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_alert")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeBooleanT isAlertField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "client_dialects")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT clientDialectsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "app_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT appNameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "start_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT startTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "duration")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT durationField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "malware")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectMalware malwareField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "severity_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT severityIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "share")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT shareField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "tls")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectTls tlsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "proxy_http_response")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectHttpResponse proxyHttpResponseField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "share_type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT shareTypeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "device")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectDevice deviceField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "class_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT classUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "disposition")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT dispositionField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "count")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT countField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "file")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectFile fileField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "activity_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT activityIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "src_endpoint")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectNetworkEndpoint srcEndpointField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "proxy_endpoint")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectNetworkProxy proxyEndpointField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "risk_details")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT riskDetailsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "severity")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT severityField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "end_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT endTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "confidence_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT confidenceIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "connection_info")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectNetworkConnectionInfo connectionInfoField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "message_context")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectMessageContext messageContextField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "dst_endpoint")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectNetworkEndpoint dstEndpointField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "load_balancer")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectLoadBalancer loadBalancerField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "risk_level_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT riskLevelIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "disposition_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT dispositionIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "raw_data_size")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT rawDataSizeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "actor")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectActor actorField;
}
