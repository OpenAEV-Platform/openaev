package io.openaev.database.raw;

import java.time.Instant;

/**
 * Spring Data projection interface for expectation trace data.
 *
 * <p>This interface defines a projection for retrieving expectation trace information. Traces
 * represent evidence from security platforms (SIEM, EDR, etc.) that supports or refutes inject
 * expectation results.
 *
 * @see io.openaev.database.model.InjectExpectationTrace
 */
public interface RawExpectationTrace {

  /**
   * Returns the unique identifier of the expectation trace.
   *
   * @return the trace ID
   */
  String getInject_expectation_trace_id();

  /**
   * Returns the ID of the expectation this trace is associated with.
   *
   * @return the expectation ID
   */
  String getInject_expectation_trace_expectation();

  /**
   * Returns the source system identifier that generated this trace.
   *
   * @return the source ID
   */
  String getInject_expectation_trace_source_id();

  /**
   * Returns the name of the alert from the security platform.
   *
   * @return the alert name
   */
  String getInject_expectation_trace_alert_name();

  /**
   * Returns the URL link to the alert in the source security platform.
   *
   * @return the alert link URL
   */
  String getInject_expectation_trace_alert_link();

  /**
   * Returns the timestamp when the trace event occurred.
   *
   * @return the event timestamp
   */
  Instant getInject_expectation_trace_date();

  /**
   * Returns the creation timestamp of the trace record.
   *
   * @return the creation timestamp
   */
  Instant getInject_expectation_trace_created_at();

  /**
   * Returns the last update timestamp of the trace record.
   *
   * @return the update timestamp
   */
  Instant getInject_expectation_trace_updated_at();
}
