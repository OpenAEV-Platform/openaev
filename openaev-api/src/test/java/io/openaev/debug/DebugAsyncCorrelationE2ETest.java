package io.openaev.debug;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.openaev.IntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.TestPropertySource;

/**
 * Verifies trace id propagation across a real Spring async boundary. Work submitted to the existing
 * context-aware {@code taskLoggerExecutor} (which copies the MDC into the worker thread) keeps the
 * caller's trace id, so both the application log lines and the SQL log lines emitted in the async
 * task are correlated with the originating request.
 *
 * <p>This covers the Spring task-executor boundary only. Quartz and RabbitMQ use other threads/
 * processes and do not go through this executor; their end-to-end propagation remains out of scope.
 *
 * <p>The property set matches {@code DebugModeE2ETest} so the booted context is shared (no extra
 * startup).
 */
@TestPropertySource(
    properties = {
      "openaev.debug.enabled=true",
      "openaev.debug.jfr.settings=default",
      "openaev.debug.output-dir=target/debug-e2e",
      "openaev.debug.jfr.duration=1h"
    })
@DisplayName("Debug mode: trace id propagation across the Spring async executor")
class DebugAsyncCorrelationE2ETest extends IntegrationTest {

  @Autowired private Tracer tracer;
  @Autowired private DataSource dataSource;

  @Autowired
  @Qualifier("taskLoggerExecutor")
  private Executor taskLoggerExecutor;

  private Logger root;
  private Logger sqlLogger;
  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void attachAppender() {
    root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    // SQL logger is routed to a file with additivity=false, so capture it directly too.
    sqlLogger = (Logger) LoggerFactory.getLogger("io.openaev.debug.sql");
    sqlLogger.setLevel(Level.INFO);
    appender = new ListAppender<>();
    appender.start();
    root.addAppender(appender);
    sqlLogger.addAppender(appender);
  }

  @AfterEach
  void detachAppender() {
    root.detachAppender(appender);
    sqlLogger.detachAppender(appender);
  }

  @Test
  @DisplayName("async work keeps the caller's trace id on both app logs and SQL logs")
  void traceIdPropagatesAcrossAsyncBoundary() throws Exception {
    appender.list.clear();
    CompletableFuture<String> childTraceId = new CompletableFuture<>();
    CountDownLatch done = new CountDownLatch(1);

    Span span = tracer.nextSpan().name("request").start();
    String expectedTraceId;
    try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
      expectedTraceId = span.context().traceId();

      // Submitted while in scope: the executor's TaskDecorator copies the MDC (with the trace id)
      // into the worker thread.
      taskLoggerExecutor.execute(
          () -> {
            try {
              childTraceId.complete(MDC.get("traceId"));
              try (Connection c = dataSource.getConnection();
                  PreparedStatement ps =
                      c.prepareStatement("select user_id from users where user_id = ?")) {
                ps.setString(1, "async-probe");
                ps.executeQuery().close();
              }
            } catch (Exception e) {
              childTraceId.completeExceptionally(e);
            } finally {
              done.countDown();
            }
          });
    }

    assertThat(done.await(5, TimeUnit.SECONDS)).as("async task should complete").isTrue();
    span.end();

    // The worker thread saw the caller's trace id in its MDC.
    assertThat(childTraceId.get(1, TimeUnit.SECONDS)).isEqualTo(expectedTraceId);

    // The SQL executed inside the async task is logged and carries that same trace id.
    List<ILoggingEvent> events = new ArrayList<>(appender.list);
    assertThat(events)
        .as("SQL emitted in the async task is correlated with the caller")
        .anyMatch(
            e ->
                "io.openaev.debug.sql".equals(e.getLoggerName())
                    && e.getFormattedMessage().contains("where user_id = ?")
                    && expectedTraceId.equals(e.getMDCPropertyMap().get("traceId")));
  }
}
