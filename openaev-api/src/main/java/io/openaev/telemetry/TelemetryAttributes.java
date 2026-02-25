package io.openaev.telemetry;

import io.opentelemetry.api.common.AttributeKey;

/**
 * Centralizes OpenTelemetry attribute keys used for telemetry events. Keys follow the <a
 * href="https://opentelemetry.io/docs/specs/semconv/">OTel Semantic Conventions</a> naming style.
 */
public final class TelemetryAttributes {

  private TelemetryAttributes() {}

  public static final AttributeKey<String> EVENT_TYPE = AttributeKey.stringKey("event.type");
  public static final AttributeKey<String> CREATED_AT = AttributeKey.stringKey("event.created_at");
}
