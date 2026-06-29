package io.openaev.debug;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import javax.sql.DataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * End-to-end test of the SQL logging path: a real datasource-proxy wraps a real H2 datasource, the
 * real masking listener is attached and statements are executed through JDBC. No mocks.
 */
@DisplayName("SQL logging + masking (real proxy over H2)")
class SqlLoggingMaskingTest {

  private static final String JWT =
      "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";

  private Logger sqlLogger;
  private ListAppender<ILoggingEvent> appender;
  private DataSource proxyDataSource;

  @BeforeEach
  void setUp() throws Exception {
    JdbcDataSource h2 = new JdbcDataSource();
    h2.setURL("jdbc:h2:mem:debug-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
    h2.setUser("sa");

    SensitiveDataMasker masker = new SensitiveDataMasker(new DebugProperties().getMasking());
    MaskingSqlLoggingListener listener =
        new MaskingSqlLoggingListener(
            masker, new DebugRuntimeState(), new DebugProperties().getSql());
    proxyDataSource = ProxyDataSourceBuilder.create(h2).name("test").listener(listener).build();

    try (Connection c = proxyDataSource.getConnection();
        Statement s = c.createStatement()) {
      s.execute(
          "create table users (user_id varchar, user_email varchar, user_password varchar, token varchar)");
    }

    sqlLogger = (Logger) LoggerFactory.getLogger("io.openaev.debug.sql");
    sqlLogger.setLevel(Level.INFO);
    appender = new ListAppender<>();
    appender.start();
    sqlLogger.addAppender(appender);
  }

  @AfterEach
  void tearDown() {
    sqlLogger.detachAppender(appender);
  }

  @Test
  @DisplayName("logs statement with timing and masks secrets and PII in parameters")
  void logsAndMasks() throws Exception {
    try (Connection c = proxyDataSource.getConnection();
        PreparedStatement ps =
            c.prepareStatement(
                "insert into users (user_id, user_email, user_password, token) values (?, ?, ?, ?)")) {
      ps.setString(1, "id-123");
      ps.setString(2, "alice@example.com");
      ps.setString(3, "hunter2");
      ps.setString(4, JWT);
      ps.executeUpdate();
    }

    String logged =
        appender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .filter(m -> m.contains("insert into users"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no SQL log line captured"));

    // Statement, timing and the non-sensitive parameter are present.
    assertThat(logged).contains("time=").contains("user_id=id-123");

    // The password (sensitive column) is masked, its value never appears.
    assertThat(logged).contains("user_password=***MASKED***").doesNotContain("hunter2");

    // PII (email) and the JWT are masked by value pattern, even though one column is not
    // "sensitive".
    assertThat(logged).doesNotContain("alice@example.com");
    assertThat(logged).doesNotContain("eyJ");
  }
}
