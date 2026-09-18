package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectTrace extends OcsfObject {
  /**
   * The total time, in milliseconds, that the trace covers, calculated as the difference between
   * start_time and end_time. This duration helps assess the overall performance of a request as it
   * travels across various services, and is essential for identifying latency and potential
   * bottlenecks within the distributed system. The trace duration may differ from individual span
   * durations due to the propagation and processing times of the trace as it spans multiple
   * components.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "duration")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT durationField;

  /**
   * The end timestamp of the trace, essential for identifying latency and performance bottlenecks.
   * Like the start time, this timestamp is normalized across the trace system to ensure
   * consistency, even when events are recorded across distributed services with unsynchronized
   * clocks. Normalized time allows for accurate trace duration calculations and helps observability
   * tools track overall performance across services, regardless of the individual system time
   * settings.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "end_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT endTimeDtField;

  /**
   * The end timestamp of the trace, essential for identifying latency and performance bottlenecks.
   * Like the start time, this timestamp is normalized across the trace system to ensure
   * consistency, even when events are recorded across distributed services with unsynchronized
   * clocks. Normalized time allows for accurate trace duration calculations and helps observability
   * tools track overall performance across services, regardless of the individual system time
   * settings.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "end_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT endTimeField;

  /**
   * The flags associated with the trace, used to indicate specific properties or behaviors, such as
   * whether the trace is sampled or if it has special handling. Flags help control how traces are
   * processed, logged, and analyzed, providing valuable context for tracing and observability tools
   * in identifying trace characteristics or specific tracking requirements.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "flags")
  private java.util.List<io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT> flagsField;

  /**
   * Identifies the service or component generating the trace, helping to track and correlate the
   * flow of requests through various parts of a distributed system. This information is essential
   * for understanding the role and performance of specific services within the broader context of
   * system operations and for diagnosing issues across different components.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "service")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectService serviceField;

  /**
   * Represents a single unit of work or operation within a distributed trace. A span typically
   * tracks the execution of a request across a service, capturing important details such as the
   * operation, timestamps, and status. Spans help break down the overall trace into smaller,
   * manageable parts, enabling detailed analysis of the performance and behavior of specific
   * operations within the system. They are crucial for understanding latency, dependencies, and
   * bottlenecks in complex distributed systems.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "span")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectSpan spanField;

  /**
   * The start timestamp of the trace, essential for identifying latency and performance
   * bottlenecks. Like the end time, this timestamp is normalized across the trace system to ensure
   * consistency, even when events are recorded across distributed services with unsynchronized
   * clocks. Normalized time enables accurate trace duration calculations and helps observability
   * tools track performance across services, regardless of the individual system time settings.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "start_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT startTimeDtField;

  /**
   * The start timestamp of the trace, essential for identifying latency and performance
   * bottlenecks. Like the end time, this timestamp is normalized across the trace system to ensure
   * consistency, even when events are recorded across distributed services with unsynchronized
   * clocks. Normalized time enables accurate trace duration calculations and helps observability
   * tools track performance across services, regardless of the individual system time settings.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "start_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT startTimeField;

  /**
   * The unique identifier of the trace used in distributed systems and microservices architecture
   * to track and correlate requests across various components of an application.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;
}
