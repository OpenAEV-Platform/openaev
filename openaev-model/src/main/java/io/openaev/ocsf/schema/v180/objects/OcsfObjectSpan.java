package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectSpan extends OcsfObject {
  /**
   * The total time, in milliseconds, that the span represents, calculated as the difference between
   * start_time and end_time. It reflects the operation's performance and latency, independent of
   * event timestamps, and accounts for normalized times used by observability tools to ensure
   * consistency across distributed systems.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "duration")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeLongT durationField;

  /**
   * The end timestamp of the span, essential for identifying latency and performance bottlenecks.
   * Like the start time, this timestamp is normalized across the observability system to ensure
   * consistency, even when events are recorded across distributed services with unsynchronized
   * clocks. Normalized time allows for accurate duration calculations and helps observability tools
   * track performance across services, regardless of the individual system time settings.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "end_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT endTimeDtField;

  /**
   * The end timestamp of the span, essential for identifying latency and performance bottlenecks.
   * Like the start time, this timestamp is normalized across the observability system to ensure
   * consistency, even when events are recorded across distributed services with unsynchronized
   * clocks. Normalized time allows for accurate duration calculations and helps observability tools
   * track performance across services, regardless of the individual system time settings.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "end_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT endTimeField;

  /**
   * The message in a span (often referred to as a span event) serves as a way to record significant
   * moments or occurrences during the span's lifecycle. This content typically manifests as log
   * entries, annotations, or semi-structured events as a string, providing additional granularity
   * and context about what happens at specific points during the execution of an operation.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "message")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT messageField;

  /**
   * Describes an action performed in a span, such as API requests, database queries, or
   * computations.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "operation")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT operationField;

  /**
   * The ID of the parent span for this span object, establishing its relationship in the trace
   * hierarchy.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "parent_uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT parentUidField;

  /**
   * Identifies the service or component that generates the span, helping trace its path through the
   * distributed system.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "service")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectService serviceField;

  /**
   * The start timestamp of the span, essential for identifying latency and performance bottlenecks.
   * This timestamp is normalized across the observability system, ensuring consistency even when
   * events occur across distributed services with potentially unsynchronized clocks. By using
   * normalized time, observability tools can provide accurate, uniform measurements of operation
   * performance and latency, regardless of where or when the events actually occur.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "start_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT startTimeDtField;

  /**
   * The start timestamp of the span, essential for identifying latency and performance bottlenecks.
   * This timestamp is normalized across the observability system, ensuring consistency even when
   * events occur across distributed services with potentially unsynchronized clocks. By using
   * normalized time, observability tools can provide accurate, uniform measurements of operation
   * performance and latency, regardless of where or when the events actually occur.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "start_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT startTimeField;

  /**
   * Indicates the outcome of the operation in the span, such as success, failure, or error. Issues
   * in a span typically refer to problems such as failed operations, timeouts, service
   * unavailability, or errors in processing that can negatively impact the performance or
   * reliability of the system. Tracking the `status_code` helps pinpoint these issues, enabling
   * quicker identification and resolution of system inefficiencies or faults.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "status_code")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT statusCodeField;

  /**
   * The unique identifier for the span, used in distributed systems and microservices architectures
   * to track and correlate requests across different components of an application. It enables
   * tracing the flow of a request through various services.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;
}
