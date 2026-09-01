package io.openaev.ocsf.schema.v180.classes;

import io.openaev.ocsf.schema.OcsfClass;

@lombok.Getter
public class OcsfClassDetectionFinding extends OcsfClass {
  /** The normalized caption of <code>action_id</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "action")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT actionField;

  /**
   * The action taken by a control or other policy-based system leading to an outcome or
   * disposition. An unknown action may still correspond to a known disposition. Refer to <code>
   * disposition_id</code> for the outcome of the action.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "action_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT actionIdField;

  /** The normalized identifier of the finding activity. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "activity_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT activityIdField;

  /** The finding activity name, as defined by the <code>activity_id</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "activity_name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT activityNameField;

  /**
   * The actor object describes details about the user/role/process that was the source of the
   * activity. Note that this is not the threat actor of a campaign but may be part of a campaign.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "actor")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectActor actorField;

  /**
   * Describes baseline information about normal activity patterns, along with any detected
   * deviations or anomalies that triggered this finding.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "anomaly_analyses")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectAnomalyAnalysis>
      anomalyAnalysesField;

  /** Describes details about a typical API (Application Programming Interface) call. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "api")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectApi apiField;

  /** The details of the user assigned to an Incident. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "assignee")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectUser assigneeField;

  /** The details of the group assigned to an Incident. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "assignee_group")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectGroup assigneeGroupField;

  /**
   * An array of MITRE ATT&CK® objects describing identified tactics, techniques & sub-techniques.
   * The objects are compatible with MITRE ATLAS™ tactics, techniques & sub-techniques.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "attacks")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectAttack> attacksField;

  /**
   * Provides details about an authorization, such as authorization outcome, and any associated
   * policies related to the activity/event.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "authorizations")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectAuthorization>
      authorizationsField;

  /** The event category name, as defined by category_uid value: <code>Findings</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "category_name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT categoryNameField;

  /** The category unique identifier of the event. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "category_uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT categoryUidField;

  /** The event class name, as defined by class_uid value: <code>Detection Finding</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "class_name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT classNameField;

  /** The unique identifier of a class. A class describes the attributes available in an event. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "class_uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT classUidField;

  /** Describes details about the Cloud environment where the event or finding was created. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "cloud")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectCloud cloudField;

  /** A user provided comment about the finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "comment")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT commentField;

  /**
   * The confidence, normalized to the caption of the confidence_id value. In the case of 'Other',
   * it is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "confidence")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT confidenceField;

  /**
   * The normalized confidence refers to the accuracy of the rule that created the finding. A rule
   * with a low confidence means that the finding scope is wide and may create finding reports that
   * may not be malicious in nature.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "confidence_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT confidenceIdField;

  /** The confidence score as reported by the event source. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "confidence_score")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT confidenceScoreField;

  /**
   * The number of times that events in the same logical group occurred during the event
   * <strong>Start Time</strong> to <strong>End Time</strong> period.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "count")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT countField;

  /**
   * Describes the affected device/host. If applicable, it can be used in conjunction with <code>
   * Resource(s)</code>.
   *
   * <p>e.g. Specific details about an AWS EC2 instance, that is affected by the Finding.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "device")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectDevice deviceField;

  /**
   * The disposition name, normalized to the caption of the disposition_id value. In the case of
   * 'Other', it is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "disposition")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT dispositionField;

  /**
   * Describes the outcome or action taken by a security control, such as access control checks,
   * malware detections or various types of policy violations.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "disposition_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT dispositionIdField;

  /**
   * The event duration or aggregate time, the amount of time the event covers from <code>start_time
   * </code> to <code>end_time</code> in milliseconds.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "duration")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeLongT durationField;

  /** The time of the most recent event included in the finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "end_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT endTimeDtField;

  /** The time of the most recent event included in the finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "end_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT endTimeField;

  /**
   * The additional information from an external data source, which is associated with the event or
   * a finding. For example add location information for the IP address in the DNS answers:<code>
   * [{"name": "answers.ip", "value": "92.24.47.250", "type": "location", "data": {"city": "Socotra", "continent": "Asia", "coordinates": [-25.4153, 17.0743], "country": "YE", "desc": "Yemen"}}]
   * </code>
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "enrichments")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectEnrichment> enrichmentsField;

  /**
   * Describes various evidence artifacts associated to the activity/activities that triggered a
   * security detection.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "evidences")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectEvidences> evidencesField;

  /** Describes the supporting information about a generated finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "finding_info")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectFindingInfo findingInfoField;

  /** The firewall rule that pertains to the control that triggered the event, if applicable. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "firewall_rule")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectFirewallRule firewallRuleField;

  /**
   * The impact , normalized to the caption of the impact_id value. In the case of 'Other', it is
   * defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "impact")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT impactField;

  /**
   * The normalized impact of the incident or finding. Per NIST, this is the magnitude of harm that
   * can be expected to result from the consequences of unauthorized disclosure, modification,
   * destruction, or loss of information or information system availability.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "impact_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT impactIdField;

  /** The impact as an integer value of the finding, valid range 0-100. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "impact_score")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT impactScoreField;

  /**
   * Indicates that the event is considered to be an alertable signal. For example, an <code>
   * activity_id</code> of 'Create' could constitute an alertable signal and the value would be
   * <code>true</code>, while 'Close' likely would not and either omit the attribute or set its
   * value to <code>false</code>. Note that other events with the <code>security_control</code>
   * profile may also be deemed alertable signals and may also carry <code>is_alert = true</code>
   * attributes.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_alert")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT isAlertField;

  /** A determination based on analytics as to whether a potential breach was found. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_suspected_breach")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT isSuspectedBreachField;

  /** Describes malware reported in a Detection Finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "malware")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectMalware> malwareField;

  /** Describes details about malware scan job that triggered this Detection Finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "malware_scan_info")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectMalwareScanInfo malwareScanInfoField;

  /** The description of the event/finding, as defined by the source. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "message")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT messageField;

  /** The metadata associated with the event or a finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "metadata")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectMetadata metadataField;

  /** The observables associated with the event or a finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "observables")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectObservable> observablesField;

  /**
   * The OSINT (Open Source Intelligence) object contains details related to an indicator such as
   * the indicator itself, related indicators, geolocation, registrar information, subdomains,
   * analyst commentary, and other contextual information. This information can be used to further
   * enrich a detection or finding by providing decisioning support to other analysts and engineers.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "osint")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectOsint> osintField;

  /**
   * The policy that pertains to the control that triggered the event, if applicable. For example
   * the name of an anti-malware policy or an access control policy.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "policy")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectPolicy policyField;

  /**
   * The priority, normalized to the caption of the priority_id value. In the case of 'Other', it is
   * defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "priority")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT priorityField;

  /**
   * The normalized priority. Priority identifies the relative importance of the incident or
   * finding. It is a measurement of urgency.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "priority_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT priorityIdField;

  /** The raw event/finding data as received from the source. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "raw_data")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT rawDataField;

  /** The hash, which describes the content of the raw_data field. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "raw_data_hash")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectFingerprint rawDataHashField;

  /** The size of the raw data which was transformed into an OCSF event, in bytes. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "raw_data_size")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeLongT rawDataSizeField;

  /** Describes the recommended remediation steps to address identified issue(s). */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "remediation")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectRemediation remediationField;

  /**
   * Describes details about resources that were the target of the activity that triggered the
   * finding.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "resources")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectResourceDetails>
      resourcesField;

  /** Describes the risk associated with the finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "risk_details")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT riskDetailsField;

  /** The risk level, normalized to the caption of the risk_level_id value. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "risk_level")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT riskLevelField;

  /** The normalized risk level id. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "risk_level_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT riskLevelIdField;

  /** The risk score as reported by the event source. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "risk_score")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT riskScoreField;

  /**
   * The event/finding severity, normalized to the caption of the <code>severity_id</code> value. In
   * the case of 'Other', it is defined by the source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "severity")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT severityField;

  /**
   * The normalized identifier of the event/finding severity.The normalized severity is a
   * measurement the effort and expense required to manage and resolve an event or incident. Smaller
   * numerical values represent lower impact events, and larger numerical values represent higher
   * impact events.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "severity_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT severityIdField;

  /** A Url link used to access the original incident. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "src_url")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeUrlT srcUrlField;

  /** The time of the least recent event included in the finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "start_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT startTimeDtField;

  /** The time of the least recent event included in the finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "start_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT startTimeField;

  /**
   * The event status code, as reported by the event source.<br>
   * <br>
   * For example, in a Windows Failed Authentication event, this would be the value of 'Failure
   * Code', e.g. 0x18.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "status_code")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT statusCodeField;

  /** The status detail contains additional information about the event/finding outcome. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "status_detail")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT statusDetailField;

  /**
   * The normalized status of the Finding set by the consumer normalized to the caption of the
   * status_id value. In the case of 'Other', it is defined by the source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "status")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT statusField;

  /** The normalized status identifier of the Finding, set by the consumer. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "status_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT statusIdField;

  /** The linked ticket in the ticketing system. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "ticket")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectTicket ticketField;

  /**
   * The associated ticket(s) in the ticketing system. Each ticket contains details like ticket ID,
   * status, etc.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "tickets")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectTicket> ticketsField;

  /** The normalized event occurrence time or the finding creation time. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT timeDtField;

  /** The normalized event occurrence time or the finding creation time. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT timeField;

  /**
   * The number of minutes that the reported event <code>time</code> is ahead or behind UTC, in the
   * range -1,080 to +1,080.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "timezone_offset")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT timezoneOffsetField;

  /** The event/finding type name, as defined by the type_uid. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT typeNameField;

  /**
   * The event/finding type ID. It identifies the event's semantics and structure. The value is
   * calculated by the logging system as: <code>class_uid * 100 + activity_id</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeLongT typeUidField;

  /**
   * The attributes that are not mapped to the event schema. The names and values of those
   * attributes are specific to the event source.
   */
  @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
      using = io.openaev.ocsf.schema.v180.ObjectNodeDeserialiser.class)
  @com.fasterxml.jackson.annotation.JsonProperty(value = "unmapped")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeJsonT unmappedField;

  /**
   * The Vendor Attributes object can be used to represent values of attributes populated by the
   * Vendor/Finding Provider. It can help distinguish between the vendor-provided values and
   * consumer-updated values, of key attributes like <code>severity_id</code>.<br>
   * The original finding producer should not populate this object. It should be populated by
   * consuming systems that support data mutability.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "vendor_attributes")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectVendorAttributes vendorAttributesField;

  /** The verdict assigned to an Incident finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "verdict")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT verdictField;

  /** The normalized verdict of an Incident. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "verdict_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT verdictIdField;

  /** Describes vulnerabilities reported in a Detection Finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "vulnerabilities")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectVulnerability>
      vulnerabilitiesField;
}
