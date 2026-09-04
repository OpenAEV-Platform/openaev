package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectLogger extends OcsfObject {
  /** The device where the events are logged. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "device")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectDevice deviceField;

  /** The unique identifier of the event assigned by the logger. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "event_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT eventUidField;

  /**
   * Indicates whether the OCSF event data has been truncated due to size limitations. When <code>
   * true</code>, some event data may have been omitted to fit within system constraints.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_truncated")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeBooleanT isTruncatedField;

  /** The format of data in the log. For example JSON, syslog or CSV. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "log_format")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT logFormatField;

  /**
   * The level at which an event was logged. This can be log provider specific. For example the
   * audit level.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "log_level")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT logLevelField;

  /**
   * The log name for the logging provider log, or the file name of the system log. This may be an
   * intermediate store-and-forward log or a vendor destination log. For example
   * /archive/server1/var/log/messages.0 or /var/log/.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "log_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT logNameField;

  /**
   * The logging provider or logging service that logged the event. This may be an intermediate
   * application store-and-forward log or a vendor destination log.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "log_provider")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT logProviderField;

  /**
   * The event log schema version of the original event. For example the syslog version or the Cisco
   * Log Schema version
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "log_version")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT logVersionField;

  /**
   * The time when this logger received and logged the event. For the last logger in the pipeline,
   * this value should match <code>metadata.logged_time</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "logged_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT loggedTimeDtField;

  /**
   * The time when this logger received and logged the event. For the last logger in the pipeline,
   * this value should match <code>metadata.logged_time</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "logged_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT loggedTimeField;

  /** The name of the logging product instance. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT nameField;

  /**
   * The product logging the event. This may be the event source product, a management server
   * product, a scanning product, a SIEM, etc.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "product")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectProduct productField;

  /** The time when the event was transmitted from the logging device to it's next destination. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "transmit_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT transmitTimeDtField;

  /** The time when the event was transmitted from the logging device to it's next destination. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "transmit_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT transmitTimeField;

  /** The unique identifier of the logging product instance. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;

  /**
   * The <code>uid</code> attribute in numeric form where applicable.<br>
   * <strong>Note:</strong> Producers may populate <code>uid_numeric</code> only in addition to
   * <code>uid</code> and not as an alternative to it.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_numeric")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT uidNumericField;

  /**
   * The original size of the OCSF event data in kilobytes before any truncation occurred. This
   * field is typically populated when <code>is_truncated</code> is <code>true</code> to indicate
   * the full size of the original event.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "untruncated_size")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT untruncatedSizeField;

  /** The version of the logging provider. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT versionField;
}
