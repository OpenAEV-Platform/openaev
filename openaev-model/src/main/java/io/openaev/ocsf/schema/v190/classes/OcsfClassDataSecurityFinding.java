package io.openaev.ocsf.schema.v190.classes;

import io.openaev.ocsf.schema.OcsfClass;

@lombok.Getter
public class OcsfClassDataSecurityFinding extends OcsfClass {
  /** The normalized caption of <code>action_id</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "action")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT actionField;

  /**
   * The action taken by a control or other policy-based system leading to an outcome or
   * disposition. An unknown action may still correspond to a known disposition. Refer to <code>
   * disposition_id</code> for the outcome of the action.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "action_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT actionIdField;

  /** The normalized identifier of the Data Security Finding activity. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "activity_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT activityIdField;

  /** The Data Security finding activity name, as defined by the <code>activity_id</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "activity_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT activityNameField;

  /**
   * Describes details about the actor implicated in the data security finding. Either an actor that
   * owns a particular digital file or information store, or an actor which accessed classified or
   * sensitive data.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "actor")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectActor actorField;

  /** Describes details about a typical API (Application Programming Interface) call. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "api")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectApi apiField;

  /** The details of the user assigned to an Incident. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "assignee")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectUser assigneeField;

  /** The details of the group assigned to an Incident. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "assignee_group")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectGroup assigneeGroupField;

  /**
   * An array of MITRE ATT&CK® objects describing identified tactics, techniques & sub-techniques.
   * The objects are compatible with MITRE ATLAS™ tactics, techniques & sub-techniques.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "attacks")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectAttack> attacksField;

  /**
   * One or more cryptographic <code>attestation</code> objects over an event, each providing
   * integrity, authenticity, and non-repudiation. Independent attesters over the same event — for
   * example a producer at write time and a downstream processor at ingest — each contribute a
   * separate attestation, distinct from co-signers on a single attestation, which share its <code>
   * signatures</code> array. Carried on the <code>record_integrity</code> profile; the object
   * itself is domain-agnostic.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "attestation_list")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectAttestation>
      attestationListField;

  /**
   * Provides details about an authorization, such as authorization outcome, and any associated
   * policies related to the activity/event.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "authorizations")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectAuthorization>
      authorizationsField;

  /** The event category name, as defined by category_uid value: <code>Findings</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "category_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT categoryNameField;

  /**
   * The category unique identifier of the event. Each event class belongs to exactly one category.
   * Producers and mappers must set this to the category defined by the event class being used.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "category_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT categoryUidField;

  /** The event class name, as defined by class_uid value: <code>Data Security Finding</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "class_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT classNameField;

  /**
   * The unique identifier of a class. A class describes the attributes available in an event.
   * Producers and mappers must set this to the <code>uid</code> defined in the event class
   * definition. For example, <code>Detection Finding</code> is <code>2004</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "class_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT classUidField;

  /** Describes details about the Cloud environment where the event or finding was created. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "cloud")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectCloud cloudField;

  /** A user provided comment about the finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "comment")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT commentField;

  /**
   * The confidence, normalized to the caption of the confidence_id value. In the case of 'Other',
   * it is defined by the event source and should be the same as <code>confidence_score</code> if
   * also populated..
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "confidence")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT confidenceField;

  /**
   * The normalized confidence refers to the accuracy of the analytics that produced the finding. An
   * analytic with a low confidence means that the finding scope is wide and may create finding
   * reports that are noisy or may not be malicious in nature.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "confidence_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT confidenceIdField;

  /**
   * The confidence score as reported by the event source. If <code>confidence_id</code> is 99
   * 'Other', it should be the same as <code>confidence</code> if both are populated.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "confidence_score")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT confidenceScoreField;

  /**
   * The number of events aggregated into this single record. Only populate for aggregate events.
   * When set, <code>start_time</code> and <code>end_time</code> should also be provided to define
   * the aggregation window.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "count")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT countField;

  /**
   * The Data Security object describes the characteristics, techniques and content of a Data Loss
   * Prevention (DLP), Data Loss Detection (DLD), Data Classification, or similar tools' finding,
   * alert, or detection mechanism(s).
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "data_security")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectDataSecurity dataSecurityField;

  /**
   * Describes the database where classified or sensitive data is stored in, or was accessed from.
   * Databases are typically datastore services that contain an organized collection of structured
   * and/or semi-structured data.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "database")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectDatabase databaseField;

  /**
   * Describes the databucket where classified or sensitive data is stored in, or was accessed from.
   * The data bucket object is a basic container that holds data, typically organized through the
   * use of data partitions.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "databucket")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectDatabucket databucketField;

  /** Describes the device where classified or sensitive data is stored in, or was accessed from. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "device")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectDevice deviceField;

  /**
   * The disposition name, normalized to the caption of the disposition_id value. In the case of
   * 'Other', it is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "disposition")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT dispositionField;

  /**
   * Describes the outcome or action taken by a security control, such as access control checks,
   * malware detections or various types of policy violations.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "disposition_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT dispositionIdField;

  /**
   * Describes the endpoint where classified or sensitive data is stored in, or was accessed from.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "dst_endpoint")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectNetworkEndpoint dstEndpointField;

  /**
   * The elapsed time of the aggregation window in milliseconds, from <code>start_time</code> to
   * <code>end_time</code>. Only populate for aggregate events (<code>count</code> &gt; 1). The
   * value should equal <code>end_time - start_time</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "duration")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT durationField;

  /** The time of the most recent event or finding that contributed to this finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "end_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT endTimeDtField;

  /** The time of the most recent event or finding that contributed to this finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "end_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT endTimeField;

  /**
   * The additional information from an external data source, which is associated with the event or
   * a finding. For example add location information for the IP address in the DNS answers:<code>
   * [{"name": "answers.ip", "value": "92.24.47.250", "type": "location", "data": {"city": "Socotra", "continent": "Asia", "coordinates": [-25.4153, 17.0743], "country": "YE", "desc": "Yemen"}}]
   * </code>
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "enrichments")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectEnrichment> enrichmentsField;

  /** Describes a file that contains classified or sensitive data. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "file")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectFile fileField;

  /** Describes the supporting information about a generated finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "finding_info")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectFindingInfo findingInfoField;

  /** The firewall rule that pertains to the control that triggered the event, if applicable. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "firewall_rule")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectFirewallRule firewallRuleField;

  /**
   * The impact , normalized to the caption of the impact_id value. In the case of 'Other', it is
   * defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "impact")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT impactField;

  /**
   * The normalized impact of the incident or finding. Per NIST, this is the magnitude of harm that
   * can be expected to result from the consequences of unauthorized disclosure, modification,
   * destruction, or loss of information or information system availability.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "impact_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT impactIdField;

  /** The impact as an integer value of the finding, valid range 0-100. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "impact_score")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT impactScoreField;

  /**
   * Indicates that the event is considered to be an alertable signal. For example, an <code>
   * activity_id</code> of 'Create' could constitute an alertable signal and the value would be
   * <code>true</code>, while 'Close' likely would not and either omit the attribute or set its
   * value to <code>false</code>. Note that other events with the <code>security_control</code>
   * profile may also be deemed alertable signals and may also carry <code>is_alert = true</code>
   * attributes.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_alert")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeBooleanT isAlertField;

  /** A determination based on analytics as to whether a potential breach was found. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_suspected_breach")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeBooleanT isSuspectedBreachField;

  /** A list of Malware objects, describing details about the identified malware. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "malware")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectMalware> malwareField;

  /** Describes details about the scan job that identified malware on the target system. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "malware_scan_info")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectMalwareScanInfo malwareScanInfoField;

  /**
   * A human-readable description of the event, as defined by the source. This should be a concise,
   * meaningful summary suitable for display in a UI or alert notification - not a raw log line. For
   * example: <code>"User john_doe logged in from 10.0.0.1."</code> rather than a raw syslog string.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "message")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT messageField;

  /**
   * The metadata object describes the event producer, schema version, and processing information.
   * Producers and mappers <b>must</b> populate <code>metadata.product</code> to identify the data
   * source, and <code>metadata.version</code> to indicate the OCSF schema version used. Consumers
   * rely on this to interpret the event correctly.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "metadata")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectMetadata metadataField;

  /**
   * Additional notes related to the finding. Each note in the array can include a comment, the user
   * who made the comment, the time when the note was created, and the time when the note was last
   * modified (typically by the same user). Notes can be used to provide additional context,
   * explanations, or observations by analysts related to the finding over time.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "notes")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectNote> notesField;

  /**
   * The observables array surfaces key indicators and entities from the event or finding in a
   * single, consistent location for downstream correlation and detection. Each entry references an
   * attribute path within the event (e.g., <code>src_endpoint.ip</code>) along with its type and
   * value, enabling consumers to extract IOCs without parsing the full event structure.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "observables")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectObservable> observablesField;

  /**
   * The OSINT (Open Source Intelligence) object contains details related to an indicator such as
   * the indicator itself, related indicators, geolocation, registrar information, subdomains,
   * analyst commentary, and other contextual information. This information can be used to further
   * enrich a detection or finding by providing decisioning support to other analysts and engineers.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "osint")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectOsint> osintField;

  /**
   * The policy that pertains to the control that triggered the event, if applicable. For example
   * the name of an anti-malware policy or an access control policy.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "policy")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectPolicy policyField;

  /**
   * The priority, normalized to the caption of the priority_id value. In the case of 'Other', it is
   * defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "priority")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT priorityField;

  /**
   * The normalized priority. Priority identifies the relative importance of the incident or
   * finding. It is a measurement of urgency.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "priority_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT priorityIdField;

  /**
   * The original event/finding data as received from the source, before normalization into OCSF.
   * Populate this with the verbatim log line, JSON payload, or other native format for forensic and
   * debugging purposes. This field is not intended for structured querying - use the normalized
   * OCSF attributes instead.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "raw_data")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT rawDataField;

  /**
   * A fingerprint (hash) of the <code>raw_data</code> content. Use this to verify the integrity of
   * the original event data or to deduplicate events.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "raw_data_hash")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectFingerprint rawDataHashField;

  /**
   * The size of the original event data (as captured in <code>raw_data</code>) in bytes, before
   * OCSF normalization. Useful for metering and capacity planning.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "raw_data_size")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT rawDataSizeField;

  /**
   * Describes details about additional resources, where classified or sensitive data is stored in,
   * or was accessed from.
   *
   * <p>You can populate this object, if the specific resource type objects available in the class (
   * <code>database, databucket, table, file</code>) aren't sufficient; OR <br>
   * You can also choose to duplicate <code>uid, name</code> of the specific resources objects, for
   * a consistent access to resource uids across all findings.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "resources")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectResourceDetails>
      resourcesField;

  /** Describes the risk associated with the finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "risk_details")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT riskDetailsField;

  /** The risk level, normalized to the caption of the risk_level_id value. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "risk_level")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT riskLevelField;

  /** The normalized risk level id. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "risk_level_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT riskLevelIdField;

  /** The risk score as reported by the event source. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "risk_score")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT riskScoreField;

  /**
   * The event/finding severity label, normalized to the caption of the <code>severity_id</code>
   * value. When <code>severity_id</code> is <code>99</code> (Other), this attribute <b>must</b>
   * contain the source-specific severity label. For all other values, this should match the caption
   * defined for that <code>severity_id</code> enum value (e.g., <code>"High"</code> for <code>
   * severity_id: 4</code>).
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "severity")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT severityField;

  /**
   * The normalized identifier of the event/finding severity.The normalized severity is a
   * measurement the effort and expense required to manage and resolve an event or incident. Smaller
   * numerical values represent lower impact events, and larger numerical values represent higher
   * impact events.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "severity_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT severityIdField;

  /** Details about the source endpoint where classified or sensitive data was accessed from. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "src_endpoint")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectNetworkEndpoint srcEndpointField;

  /** A Url link used to access the original incident. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "src_url")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeUrlT srcUrlField;

  /** The time of the earliest event or finding that contributed to this finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "start_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT startTimeDtField;

  /** The time of the earliest event or finding that contributed to this finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "start_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT startTimeField;

  /**
   * The source-specific status or error code as reported by the event source. For example, a
   * Windows logon failure code (<code>0x18</code>), an HTTP response code (<code>403</code>), or an
   * AWS API error code. This preserves the original code for detailed troubleshooting beyond what
   * <code>status_id</code> conveys.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "status_code")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT statusCodeField;

  /**
   * A human-readable description providing additional context about the event outcome. Use this to
   * convey details that go beyond the normalized <code>status_id</code> and source-specific <code>
   * status_code</code>, such as a failure reason or error message. For example: <code>
   * "Account locked after 5 failed attempts."</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "status_detail")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT statusDetailField;

  /**
   * The finding lifecycle status label, normalized to the caption of the <code>status_id</code>
   * value. When <code>status_id</code> is <code>99</code> (Other), this <b>must</b> contain the
   * source-specific status label.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "status")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT statusField;

  /**
   * The normalized finding lifecycle status identifier. Unlike the status of an activity event,
   * which indicates the success or failure of the activity, finding status tracks the review and
   * triage workflow: whether the finding is new, being investigated, suppressed, or resolved.
   * Producers should set this to reflect the current state of the finding in their system (e.g.,
   * <code>1</code> for newly created findings, <code>4</code> when remediated).
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "status_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT statusIdField;

  /**
   * Describes the table where classified or sensitive data is stored in, or was accessed from. The
   * table object represents a table within a structured relational database, warehouse, lake, or
   * similar.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "table")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectTable tableField;

  /** The linked ticket in the ticketing system. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "ticket")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectTicket ticketField;

  /**
   * The associated ticket(s) in the ticketing system. Each ticket contains details like ticket ID,
   * status, etc.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "tickets")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectTicket> ticketsField;

  /**
   * The finding creation time - when the finding was first generated, not when the underlying
   * activity occurred. For the time range of contributing events, use <code>start_time</code> and
   * <code>end_time</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT timeDtField;

  /**
   * The finding creation time - when the finding was first generated, not when the underlying
   * activity occurred. For the time range of contributing events, use <code>start_time</code> and
   * <code>end_time</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT timeField;

  /**
   * The number of minutes that the reported event <code>time</code> is ahead or behind UTC, in the
   * range -1,080 to +1,080. This allows consumers to reconstruct the local time at the event
   * source. For example, US Eastern Standard Time is <code>-300</code>. Populate this when the
   * source provides local time zone information.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "timezone_offset")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT timezoneOffsetField;

  /**
   * The event/finding type name, combining the class and activity (e.g., <code>
   * "Detection Finding: Create"</code>). The value must match the <code>class_name</code> and
   * <code>activity_name</code> joined by <code>": "</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT typeNameField;

  /**
   * The event/finding type ID. It identifies the event's semantics and structure. Producers and
   * mappers <b>must</b> compute this as <code>class_uid * 100 + activity_id</code>. It uniquely
   * identifies the combination of event class and activity across the entire schema. For example,
   * <code>Detection Finding: Create</code> is <code>200401</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT typeUidField;

  /**
   * A container for source-specific attributes that do not map to any defined OCSF attribute. Use
   * this to preserve valuable source data that would otherwise be lost during normalization. The
   * keys and values are specific to the event source.
   *
   * <p><b>Note: </b>Consumers should not rely on a stable structure within this field. The
   * preferred approach to unmapped attributes is to create a custom extension with the desired
   * structure.
   */
  @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
      using = io.openaev.ocsf.schema.v190.ObjectNodeDeserialiser.class)
  @com.fasterxml.jackson.annotation.JsonProperty(value = "unmapped")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeJsonT unmappedField;

  /**
   * The Vendor Attributes object can be used to represent values of attributes populated by the
   * Vendor/Finding Provider. It can help distinguish between the vendor-provided values and
   * consumer-updated values, of key attributes like <code>severity_id</code>.<br>
   * The original finding producer should not populate this object. It should be populated by
   * consuming systems that support data mutability.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "vendor_attributes")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectVendorAttributes vendorAttributesField;

  /** The verdict assigned to an Incident finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "verdict")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT verdictField;

  /** The normalized verdict of an Incident. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "verdict_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT verdictIdField;
}
