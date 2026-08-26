package io.openaev.debug;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.micrometer.tracing.autoconfigure.MicrometerTracingAutoConfiguration;
import org.springframework.boot.micrometer.tracing.brave.autoconfigure.BraveAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Proves correlation: while a span is in scope, the application log lines and the SQL log line all
 * carry the same {@code traceId} in the MDC. This is the in-process correlation that requirement #1
 * (correlation id) and #2 (SQL detail) rely on, exercised through the real Micrometer Tracing
 * auto-configuration (no mocks).
 */
@DisplayName("Trace id correlation")
class TraceCorrelationTest {

  private static final String JWT =
      "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ4In0.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  BraveAutoConfiguration.class, MicrometerTracingAutoConfiguration.class))
          .withPropertyValues(
              "management.tracing.enabled=true", "management.tracing.sampling.probability=1.0");

  @Test
  @DisplayName("all log lines of one request share the same trace id, including SQL lines")
  void sameTraceIdAcrossAppAndSqlLogs() {
    runner.run(
        context -> {
          Tracer tracer = context.getBean(Tracer.class);
          DataSource proxy = sqlProxyDataSource();
          try (Connection c = proxy.getConnection();
              Statement s = c.createStatement()) {
            s.execute("create table t (id varchar, token varchar)");
          }

          Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
          // The logback LoggerContext is shared across the test JVM; a prior full @SpringBootTest
          // may
          // have pinned the application loggers to ERROR and switched the SQL logger to additivity
          // false (the rotated-file appender). Force INFO on the loggers this test uses and capture
          // the SQL logger directly, so the test does not depend on the order tests run in.
          Logger sqlLogger = (Logger) LoggerFactory.getLogger("io.openaev.debug.sql");
          Logger appLogger = (Logger) LoggerFactory.getLogger("io.openaev.test.app");
          Level originalSqlLevel = sqlLogger.getLevel();
          Level originalAppLevel = appLogger.getLevel();
          sqlLogger.setLevel(Level.INFO);
          appLogger.setLevel(Level.INFO);
          ListAppender<ILoggingEvent> appender = new ListAppender<>();
          appender.start();
          root.addAppender(appender);
          sqlLogger.addAppender(appender);

          org.slf4j.Logger app = appLogger;
          String expectedTraceId;
          Span span = tracer.nextSpan().name("request").start();
          try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            expectedTraceId = span.context().traceId();
            app.info("handling request start");
            try (Connection c = proxy.getConnection();
                PreparedStatement ps =
                    c.prepareStatement("insert into t (id, token) values (?, ?)")) {
              ps.setString(1, "id-1");
              ps.setString(2, JWT);
              ps.executeUpdate();
            }
            app.info("handling request end");
          } finally {
            span.end();
            root.detachAppender(appender);
            sqlLogger.detachAppender(appender);
            sqlLogger.setLevel(originalSqlLevel);
            appLogger.setLevel(originalAppLevel);
          }

          List<ILoggingEvent> correlated =
              appender.list.stream()
                  .filter(e -> e.getMDCPropertyMap().get("traceId") != null)
                  .toList();

          // App start/end lines and the SQL line are all present and correlated.
          assertThat(correlated).hasSizeGreaterThanOrEqualTo(3);
          Set<String> traceIds =
              correlated.stream()
                  .map(e -> e.getMDCPropertyMap().get("traceId"))
                  .collect(Collectors.toSet());
          assertThat(traceIds).containsExactly(expectedTraceId);
          assertThat(correlated).anyMatch(e -> e.getFormattedMessage().contains("insert into t"));
        });
  }

  private DataSource sqlProxyDataSource() {
    JdbcDataSource h2 = new JdbcDataSource();
    h2.setURL("jdbc:h2:mem:trace-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
    h2.setUser("sa");
    SensitiveDataMasker masker = new SensitiveDataMasker(new DebugProperties().getMasking());
    MaskingSqlLoggingListener listener =
        new MaskingSqlLoggingListener(
            masker, new DebugRuntimeState(), new DebugProperties().getSql());
    return ProxyDataSourceBuilder.create(h2).name("trace").listener(listener).build();
  }
}
