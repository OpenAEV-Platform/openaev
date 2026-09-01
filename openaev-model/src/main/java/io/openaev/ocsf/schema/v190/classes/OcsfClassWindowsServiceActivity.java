package io.openaev.ocsf.schema.v190.classes;

import io.openaev.ocsf.schema.OcsfClass;

@lombok.Getter
public class OcsfClassWindowsServiceActivity extends OcsfClass {
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

  /**
   * The normalized identifier of the activity that triggered the event. Each event class defines
   * its own set of activity values. Use 0 (Unknown) when the activity cannot be determined. Use 99
   * (Other) when the activity does not match any defined value, in which case activity_name must be
   * populated with the source-specific label.<br>
   * Refer to the <code>win_service</code> attribute for details, unless any other attribute is
   * mentioned.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "activity_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT activityIdField;

  /**
   * The event activity name, as defined by the <code>activity_id</code>. When <code>activity_id
   * </code> is <code>99</code> (Other), this attribute <b>must</b> contain the source-specific
   * activity label. For all other <code>activity_id</code> values, this must match the caption
   * defined for that <code>activity_id</code> enum value.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "activity_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT activityNameField;

  /**
   * The user, process, or service that initiated the activity on the host. For system-level events
   * this is typically a process; for IAM events it is typically a user or role.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "actor")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectActor actorField;

  /**
   * The autonomous AI agent that performed this operation. Carries model identity via <code>
   * ai_agent.ai_model</code>. Populate when the action was performed by an agent rather than a
   * direct model call.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "ai_agent")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectAiAgent aiAgentField;

  /**
   * The AI model involved in this operation. Use for direct model invocations where no autonomous
   * agent is involved. For agent-mediated operations, model identity is carried within <code>
   * ai_agent.ai_model</code> instead.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "ai_model")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectAiModel aiModelField;

  /** Describes details about a typical API (Application Programming Interface) call. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "api")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectApi apiField;

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

  /** The event category name, as defined by category_uid value: <code>System Activity</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "category_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT categoryNameField;

  /**
   * The category unique identifier of the event. Each event class belongs to exactly one category.
   * Producers and mappers must set this to the category defined by the event class being used.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "category_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT categoryUidField;

  /** The event class name, as defined by class_uid value: <code>Windows Service Activity</code>. */
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

  /**
   * The confidence, normalized to the caption of the confidence_id value. In the case of 'Other',
   * it is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "confidence")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT confidenceField;

  /**
   * The normalized confidence refers to the accuracy of the security control or alert. An analytic
   * with a low confidence means that the alerts may be noisy or may not be malicious in nature.
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
   * The delegation under whose authority this AI operation was performed. Links the event to a
   * durable authorization context independent of traces and sessions, enabling correlation of all
   * events executed under the same delegated authority.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "delegation")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectDelegation delegationField;

  /**
   * The host or device where the activity was observed. Populate with details such as hostname, IP
   * address, OS, and hardware identifiers when available from the source.
   */
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
   * The elapsed time of the aggregation window in milliseconds, from <code>start_time</code> to
   * <code>end_time</code>. Only populate for aggregate events (<code>count</code> &gt; 1). The
   * value should equal <code>end_time - start_time</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "duration")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT durationField;

  /**
   * The time of the most recent event in an aggregate (<code>count</code> &gt; 1). Do not populate
   * for discrete, point-in-time events - use <code>time</code> alone. Subclasses such as findings
   * may redefine this for their own time-range semantics.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "end_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT endTimeDtField;

  /**
   * The time of the most recent event in an aggregate (<code>count</code> &gt; 1). Do not populate
   * for discrete, point-in-time events - use <code>time</code> alone. Subclasses such as findings
   * may redefine this for their own time-range semantics.
   */
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

  /** The firewall rule that pertains to the control that triggered the event, if applicable. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "firewall_rule")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectFirewallRule firewallRuleField;

  /**
   * Indicates that the event is considered to be an alertable signal. Should be set to <code>true
   * </code> if <code>disposition_id = Alert</code> among other dispositions, and/or <code>
   * risk_level_id</code> or <code>severity_id</code> of the event is elevated. Not all control
   * events will be alertable, for example if <code>disposition_id = Exonerated</code> or <code>
   * disposition_id = Allowed</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_alert")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeBooleanT isAlertField;

  /** A list of Malware objects, describing details about the identified malware. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "malware")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectMalware> malwareField;

  /** Describes details about the scan job that identified malware on the target system. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "malware_scan_info")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectMalwareScanInfo malwareScanInfoField;

  /**
   * Communication context for AI system interactions including protocols, roles, clients, and
   * session information for MCP and other AI communication systems.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "message_context")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectMessageContext messageContextField;

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

  /** The Windows service before the mutation. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "prev_win_service")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectWinService prevWinServiceField;

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

  /**
   * The time of the earliest event in an aggregate (<code>count</code> &gt; 1). Do not populate for
   * discrete, point-in-time events - use <code>time</code> alone. Subclasses such as findings may
   * redefine this for their own time-range semantics.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "start_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT startTimeDtField;

  /**
   * The time of the earliest event in an aggregate (<code>count</code> &gt; 1). Do not populate for
   * discrete, point-in-time events - use <code>time</code> alone. Subclasses such as findings may
   * redefine this for their own time-range semantics.
   */
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
   * The event status label, normalized to the caption of the <code>status_id</code> value. When
   * <code>status_id</code> is <code>99</code> (Other), this attribute <b>must</b> contain the
   * source-specific status label. For all other values, this must match the caption defined for
   * that <code>status_id</code> enum value (e.g., <code>"Success"</code> for <code>status_id: 1
   * </code>).
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "status")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT statusField;

  /**
   * The normalized status of the event outcome. Use this family of attributes to convey the outcome
   * of the activity described by the event. Producers should map their source outcome to <code>1
   * </code> (Success) or <code>2</code> (Failure). Use <code>0</code> (Unknown) when the outcome
   * cannot be determined, and <code>99</code> (Other) with a populated <code>status</code> string
   * when the source value does not map cleanly.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "status_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT statusIdField;

  /**
   * The primary timestamp of the event - when the activity actually occurred at the source. This
   * does not capture when the event record was created or serialized by the source system; for
   * event lifecycle timestamps such as ingestion and processing, use <code>metadata.logged_time
   * </code> and <code>metadata.processed_time</code> respectively, or the equivalent attributes in
   * the <code>metadata.loggers</code> array when recording pipeline stages. For aggregate events (
   * <code>count</code> &gt; 1), set this to <code>start_time</code> (the earliest OCSF <code>time
   * </code> in the aggregate) to preserve causal ordering and consistent timeline alignment. Note:
   * finding classes redefine <code>time</code> as the finding creation time rather than the
   * activity occurrence time. This <b>must</b> be a UTC epoch value in milliseconds (e.g., <code>
   * 1776881335332</code>). Mappers should use the most precise and authoritative timestamp
   * available from the source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT timeDtField;

  /**
   * The primary timestamp of the event - when the activity actually occurred at the source. This
   * does not capture when the event record was created or serialized by the source system; for
   * event lifecycle timestamps such as ingestion and processing, use <code>metadata.logged_time
   * </code> and <code>metadata.processed_time</code> respectively, or the equivalent attributes in
   * the <code>metadata.loggers</code> array when recording pipeline stages. For aggregate events (
   * <code>count</code> &gt; 1), set this to <code>start_time</code> (the earliest OCSF <code>time
   * </code> in the aggregate) to preserve causal ordering and consistent timeline alignment. Note:
   * finding classes redefine <code>time</code> as the finding creation time rather than the
   * activity occurrence time. This <b>must</b> be a UTC epoch value in milliseconds (e.g., <code>
   * 1776881335332</code>). Mappers should use the most precise and authoritative timestamp
   * available from the source.
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
  @com.fasterxml.jackson.annotation.JsonProperty(value = "unmapped")
  @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
      using = io.openaev.ocsf.schema.v190.ObjectNodeDeserialiser.class)
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeJsonT unmappedField;

  /**
   * The current Windows service. On failed or unknown status of <code>Reconfigure</code> action may
   * be populated with the intended state.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "win_service")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectWinService winServiceField;
}
