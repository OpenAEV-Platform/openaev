package io.openaev.ocsf.schema.v190.classes;

import io.openaev.ocsf.schema.OcsfClass;

@lombok.Getter
public class OcsfClassAuthentication extends OcsfClass {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "account_switch_type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT accountSwitchTypeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "account_switch_type_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT accountSwitchTypeIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "action")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT actionField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "action_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT actionIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "activity_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT activityIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "activity_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT activityNameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "actor")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectActor actorField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "ai_agent")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectAiAgent aiAgentField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "ai_model")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectAiModel aiModelField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "api")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectApi apiField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "attacks")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectAttack> attacksField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "attestation_list")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectAttestation>
      attestationListField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "auth_factors")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectAuthFactor> authFactorsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "auth_protocol")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT authProtocolField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "auth_protocol_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT authProtocolIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "authentication_token")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectAuthenticationToken
      authenticationTokenField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "authorizations")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectAuthorization>
      authorizationsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "category_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT categoryNameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "category_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT categoryUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "certificate")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectCertificate certificateField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "class_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT classNameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "class_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT classUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "cloud")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectCloud cloudField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "confidence")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT confidenceField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "confidence_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT confidenceIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "confidence_score")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT confidenceScoreField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "count")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT countField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "delegation")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectDelegation delegationField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "device")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectDevice deviceField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "disposition")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT dispositionField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "disposition_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT dispositionIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "dst_endpoint")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectNetworkEndpoint dstEndpointField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "duration")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT durationField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "end_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT endTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "end_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT endTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "enrichments")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectEnrichment> enrichmentsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "firewall_rule")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectFirewallRule firewallRuleField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "http_request")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectHttpRequest httpRequestField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "http_response")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectHttpResponse httpResponseField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_alert")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeBooleanT isAlertField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_cleartext")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeBooleanT isCleartextField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_mfa")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeBooleanT isMfaField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_new_logon")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeBooleanT isNewLogonField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_remote")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeBooleanT isRemoteField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "logon_process")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectProcess logonProcessField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "logon_type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT logonTypeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "logon_type_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT logonTypeIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "malware")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectMalware> malwareField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "malware_scan_info")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectMalwareScanInfo malwareScanInfoField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "message_context")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectMessageContext messageContextField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "message")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT messageField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "metadata")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectMetadata metadataField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "observables")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectObservable> observablesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "osint")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectOsint> osintField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "policy")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectPolicy policyField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "raw_data")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT rawDataField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "raw_data_hash")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectFingerprint rawDataHashField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "raw_data_size")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT rawDataSizeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "risk_details")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT riskDetailsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "risk_level")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT riskLevelField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "risk_level_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT riskLevelIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "risk_score")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT riskScoreField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "service")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectService serviceField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "session")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectSession sessionField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "severity")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT severityField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "severity_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT severityIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "src_endpoint")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectNetworkEndpoint srcEndpointField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "start_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT startTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "start_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT startTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "status_code")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT statusCodeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "status_detail")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT statusDetailField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "status")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT statusField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "status_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT statusIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT timeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT timeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "timezone_offset")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT timezoneOffsetField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT typeNameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT typeUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "unmapped")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectObject unmappedField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "user")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectUser userField;
}
