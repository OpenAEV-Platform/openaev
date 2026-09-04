package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectMetadata extends OcsfObject {
  /**
   * A unique identifier used to correlate this OCSF event with other related OCSF events, distinct
   * from the event's <code>uid</code> value. This enables linking multiple OCSF events that are
   * part of the same activity, transaction, or security incident across different systems or time
   * periods.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "correlation_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT correlationUidField;

  /**
   * The Data Classification object includes information about data classification levels and data
   * category types.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "data_classification")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectDataClassification dataClassificationField;

  /**
   * A list of Data Classification objects, that include information about data classification
   * levels and data category types, identified by a classifier.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "data_classifications")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectDataClassification>
      dataClassificationsField;

  /**
   * Debug information about non-fatal issues with this OCSF event. Each issue is a line in this
   * string array.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "debug")
  private java.util.List<io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT> debugField;

  /**
   * The identifier of the original event. For example the numerical Windows Event Code or Cisco
   * syslog code.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "event_code")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT eventCodeField;

  /** The schema extension used to create the event. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "extension")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectExtension extensionField;

  /** The schema extensions used to create the event. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "extensions")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectExtension> extensionsField;

  /**
   * Indicates whether the OCSF event data has been truncated due to size limitations. When <code>
   * true</code>, some event data may have been omitted to fit within system constraints.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_truncated")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeBooleanT isTruncatedField;

  /** The list of labels attached to the event. For example: <code>["sample", "dev"]</code> */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "labels")
  private java.util.List<io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT> labelsField;

  /**
   * The format of data in the log where the data originated. For example CSV, XML, Windows
   * Multiline, JSON, syslog or Cisco Log Schema.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "log_format")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT logFormatField;

  /**
   * The level at which an event was logged. This can be log provider specific. For example the
   * audit level.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "log_level")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT logLevelField;

  /**
   * The event log name, typically for the consumer of the event. For example, the storage bucket
   * name, SIEM repository index name, etc.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "log_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT logNameField;

  /**
   * The logging provider or logging service that logged the event. For example AWS CloudWatch or
   * Splunk.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "log_provider")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT logProviderField;

  /**
   * The log system or component where the data originated. For example, a file path, syslog server
   * name or a Windows hostname and logging subsystem such as Security.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "log_source")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT logSourceField;

  /**
   * The event log schema version of the original event. For example the syslog version or the Cisco
   * Log Schema version
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "log_version")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT logVersionField;

  /**
   * The ultimate logged time in the event pipeline - when the event reached its final destination
   * (e.g., SIEM, data lake). If <code>loggers</code> is populated, this should match the <code>
   * logged_time</code> of the last entry in the <code>loggers</code> array. This is distinct from
   * <code>time</code> (when the activity occurred) and <code>processed_time</code> (when an
   * intermediate system processed the event).
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "logged_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT loggedTimeDtField;

  /**
   * The ultimate logged time in the event pipeline - when the event reached its final destination
   * (e.g., SIEM, data lake). If <code>loggers</code> is populated, this should match the <code>
   * logged_time</code> of the last entry in the <code>loggers</code> array. This is distinct from
   * <code>time</code> (when the activity occurred) and <code>processed_time</code> (when an
   * intermediate system processed the event).
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "logged_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT loggedTimeField;

  /**
   * An ordered array of Logger objects describing each hop in the event pipeline between the source
   * and its final destination. Each entry captures the logging product, device, and timestamps (
   * <code>logged_time</code>, <code>transmit_time</code>) at that stage. The last entry's <code>
   * logged_time</code> should match <code>metadata.logged_time</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "loggers")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectLogger> loggersField;

  /** The time when the event was last modified or enriched. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT modifiedTimeDtField;

  /** The time when the event was last modified or enriched. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT modifiedTimeField;

  /**
   * The unique identifier assigned to the event in its original logging system before
   * transformation to OCSF format. This field preserves the source system's native event
   * identifier, enabling traceability back to the raw log entry. For example, a Windows Event
   * Record ID, a syslog message ID, a Splunk _cd value, or a database transaction log sequence
   * number.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "original_event_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT originalEventUidField;

  /**
   * The original event time as reported by the event source, preserved as a pass-through string in
   * its native format (e.g., Syslog timestamp, Windows event time). This is not normalized to
   * <code>timestamp_t</code> - the normalized equivalent is the base event <code>time</code>
   * attribute. Omit if the event is generated rather than collected via logs.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "original_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT originalTimeField;

  /**
   * The time when the event was processed by an intermediate system (e.g., ETL pipeline, event
   * processor) before reaching its final destination. Can be used with <code>logged_time</code> to
   * calculate queuing duration via <code>total_queued_duration</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "processed_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT processedTimeDtField;

  /**
   * The time when the event was processed by an intermediate system (e.g., ETL pipeline, event
   * processor) before reaching its final destination. Can be used with <code>logged_time</code> to
   * calculate queuing duration via <code>total_queued_duration</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "processed_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT processedTimeField;

  /** The product that reported the event. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "product")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectProduct productField;

  /**
   * The list of profiles used to create the event. Profiles should be referenced by their <code>
   * name</code> attribute for core profiles, or <code>extension/name</code> for profiles from
   * extensions.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "profiles")
  private java.util.List<io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT> profilesField;

  /** The entity from which the event or finding was first reported. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "reporter")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectReporter reporterField;

  /**
   * Sequence number of the event. The sequence number is a value available in some events, to make
   * the exact ordering of events unambiguous, regardless of the event time precision.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "sequence")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT sequenceField;

  /**
   * The source of the event or finding. This can be any distinguishing name for the logical origin
   * of the data — for example, 'CloudTrail Events', or a use case like 'Attack Simulations' or
   * 'Vulnerability Scans'.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "source")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT sourceField;

  /** The list of tags; <code>{key:value}</code> pairs associated to the event. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "tags")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectKeyValueObject> tagsField;

  /** The unique tenant identifier. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "tenant_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT tenantUidField;

  /**
   * The amount of time an event spent in a queue awaiting processing. In this case, the value is
   * the difference between <code>processed_time</code> and <code>logged_time</code>. This duration
   * is inclusive of all queues between the originator of the event and the intended long-term
   * storage destination of the event.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "total_queued_duration")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectTimespan totalQueuedDurationField;

  /**
   * An array of transformation info that describes the mappings or transforms applied to the data.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "transformation_info_list")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectTransformationInfo>
      transformationInfoListField;

  /** The time when the event was transmitted from the logging device to it's next destination. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "transmit_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT transmitTimeDtField;

  /** The time when the event was transmitted from the logging device to it's next destination. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "transmit_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT transmitTimeField;

  /**
   * The type of the event or finding as a subset of the <code>source</code> of the event. This can
   * be any distinguishing characteristic of the data. For example 'Management Events' or 'Device
   * Penetration Test'.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT typeField;

  /**
   * A unique identifier assigned to the OCSF event. This ID is specific to the OCSF event itself
   * and is distinct from the original event identifier in the source system (see <code>
   * original_event_uid</code>).
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;

  /**
   * The original size of the OCSF event data in kilobytes before any truncation occurred. This
   * field is typically populated when <code>is_truncated</code> is <code>true</code> to indicate
   * the full size of the original event.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "untruncated_size")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT untruncatedSizeField;

  /**
   * The version of the OCSF schema, using Semantic Versioning Specification (SemVer). For example:
   * <code>1.0.0</code>. Event consumers use the version to determine the available event
   * attributes.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT versionField;
}
