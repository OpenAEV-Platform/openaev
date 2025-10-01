package io.openaev.database.raw;

import java.time.Instant;

public interface RawExpectationTrace {
  String getInject_expectation_trace_id();

  String getInject_expectation_trace_expectation();

  String getInject_expectation_trace_source_id();

  String getInject_expectation_trace_alert_name();

  String getInject_expectation_trace_alert_link();

  Instant getInject_expectation_trace_date();

  Instant getInject_expectation_trace_created_at();

  Instant getInject_expectation_trace_updated_at();
}
