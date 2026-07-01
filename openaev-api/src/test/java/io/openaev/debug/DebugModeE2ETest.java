package io.openaev.debug;

import static io.openaev.rest.tag.TagApi.TAG_URI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.openaev.IntegrationTest;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end test of the global debug mode in a real Spring Boot context against the real
 * PostgreSQL, with the mode actually enabled. It proves the feature works through the whole stack
 * and that enabling it does not break a normal request (no regression).
 */
@TestPropertySource(
    properties = {
      "openaev.debug.enabled=true",
      "openaev.debug.jfr.settings=default",
      "openaev.debug.output-dir=target/debug-e2e",
      // Large dump interval so the recording does not churn files during the test.
      "openaev.debug.jfr.duration=1h"
    })
@DisplayName("Debug mode end-to-end (real context, real PostgreSQL)")
class DebugModeE2ETest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private DataSource dataSource;

  private Logger root;
  private Logger sqlLogger;
  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void attachAppender() {
    root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    // The SQL logger is routed to a file with additivity=false, so capture it directly too.
    sqlLogger = (Logger) LoggerFactory.getLogger("io.openaev.debug.sql");
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
  @DisplayName("debug mode is actually active: the datasource is proxied in the running app")
  void datasourceIsProxiedInRealContext() {
    assertThat(dataSource)
        .as("debug mode must wrap the auto-configured datasource")
        .isInstanceOf(ProxyDataSource.class);
  }

  @Test
  @DisplayName("the proxy still unwraps to Hikari, so actuator pool metrics/health keep working")
  void proxiedDatasourceStillUnwrapsToHikari() throws java.sql.SQLException {
    assertThat(dataSource.isWrapperFor(com.zaxxer.hikari.HikariDataSource.class)).isTrue();
    assertThat(dataSource.unwrap(com.zaxxer.hikari.HikariDataSource.class)).isNotNull();
  }

  @Test
  @WithMockUser(isAdmin = true)
  @DisplayName("a real HTTP request still succeeds and its app + SQL logs share one trace id")
  void requestSucceedsAndLogsAreCorrelated() throws Exception {
    appender.list.clear();

    mvc.perform(get(TAG_URI)).andExpect(status().isOk());

    List<ILoggingEvent> events = new ArrayList<>(appender.list);
    List<ILoggingEvent> correlated =
        events.stream().filter(e -> e.getMDCPropertyMap().get("traceId") != null).toList();

    // The request produced correlated log lines.
    assertThat(correlated).as("request should produce trace-correlated log lines").isNotEmpty();

    // Every correlated line of this request shares a single trace id.
    Set<String> traceIds =
        correlated.stream()
            .map(e -> e.getMDCPropertyMap().get("traceId"))
            .collect(Collectors.toSet());
    assertThat(traceIds).as("all log lines of one request share one trace id").hasSize(1);

    // SQL statements emitted while handling the request carry that same trace id.
    assertThat(correlated)
        .as("SQL detail is logged and correlated with the request")
        .anyMatch(e -> "io.openaev.debug.sql".equals(e.getLoggerName()));

    // The ORM per-request summary is emitted for the request and correlated too.
    assertThat(correlated)
        .as("an ORM per-request summary is emitted and correlated")
        .anyMatch(
            e ->
                "io.openaev.debug.orm".equals(e.getLoggerName())
                    && e.getFormattedMessage().contains("queries"));

    // Request log lines (controller-time app + SQL) are tagged with the tenant in the MDC.
    assertThat(correlated)
        .as("request log lines are tagged with the tenant")
        .anyMatch(e -> e.getMDCPropertyMap().get("tenant") != null);
  }

  @Test
  @DisplayName("real SQL through the proxied datasource has its secret parameter masked")
  void secretIsMaskedOnRealSql() throws Exception {
    String secret = "S3cr3t-" + UUID.randomUUID();
    appender.list.clear();

    // Raw JDBC against the real schema through the app's proxied datasource. The placeholder is
    // bound
    // to the sensitive user_password column, so it must be masked in the SQL log.
    try (Connection connection = dataSource.getConnection();
        PreparedStatement ps =
            connection.prepareStatement("select user_id from users where user_password = ?")) {
      ps.setString(1, secret);
      ps.executeQuery().close();
    }

    String logged =
        new ArrayList<>(appender.list)
            .stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(m -> m.contains("where user_password"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected SQL log line not captured"));

    assertThat(logged).contains("user_password=***MASKED***").doesNotContain(secret);
  }
}
