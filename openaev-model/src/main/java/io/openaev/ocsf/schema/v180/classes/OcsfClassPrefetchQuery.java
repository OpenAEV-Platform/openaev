package io.openaev.ocsf.schema.v180.classes;

import io.openaev.ocsf.schema.OcsfClass;

@lombok.Getter
public class OcsfClassPrefetchQuery extends OcsfClass {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "action")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT actionField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "action_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT actionIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "activity_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT activityIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "activity_name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT activityNameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "actor")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectActor actorField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "api")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectApi apiField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "attacks")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectAttack> attacksField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "authorizations")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectAuthorization>
      authorizationsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "category_name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT categoryNameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "category_uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT categoryUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "class_name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT classNameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "class_uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT classUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "cloud")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectCloud cloudField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "confidence")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT confidenceField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "confidence_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT confidenceIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "confidence_score")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT confidenceScoreField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "count")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT countField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "device")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectDevice deviceField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "disposition")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT dispositionField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "disposition_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT dispositionIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "duration")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeLongT durationField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "end_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT endTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "end_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT endTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "enrichments")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectEnrichment> enrichmentsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "firewall_rule")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectFirewallRule firewallRuleField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_alert")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT isAlertField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_run_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT lastRunTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_run_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT lastRunTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "malware")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectMalware> malwareField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "malware_scan_info")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectMalwareScanInfo malwareScanInfoField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "message")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT messageField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "metadata")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectMetadata metadataField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "observables")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectObservable> observablesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "osint")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectOsint> osintField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "policy")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectPolicy policyField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "query_info")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectQueryInfo queryInfoField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "query_result")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT queryResultField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "query_result_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT queryResultIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "raw_data")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT rawDataField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "raw_data_hash")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectFingerprint rawDataHashField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "raw_data_size")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeLongT rawDataSizeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "risk_details")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT riskDetailsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "risk_level")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT riskLevelField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "risk_level_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT riskLevelIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "risk_score")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT riskScoreField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "run_count")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT runCountField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "severity")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT severityField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "severity_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT severityIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "start_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT startTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "start_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT startTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "status_code")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT statusCodeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "status_detail")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT statusDetailField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "status")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT statusField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "status_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT statusIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT timeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT timeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "timezone_offset")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT timezoneOffsetField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT typeNameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeLongT typeUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "unmapped")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectObject unmappedField;
}
