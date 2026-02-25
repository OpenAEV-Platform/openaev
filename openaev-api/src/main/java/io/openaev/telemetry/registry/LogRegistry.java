package io.openaev.telemetry.registry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.Severity;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LogRegistry {

  @Lazy private final Logger logger;

  public void emit(String eventName, Attributes attributes) {
    logger
        .logRecordBuilder()
        .setSeverity(Severity.INFO)
        .setBody(eventName)
        .setAllAttributes(attributes)
        .emit();
  }
}
