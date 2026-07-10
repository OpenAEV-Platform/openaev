package io.openaev.debug;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import javax.sql.DataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * Covers SQL-logging scenarios beyond the basic insert: batches, updates, value-pattern masking,
 * slow-query filtering, parameter truncation, masking disabled, and that the proxy does not change
 * query results (no regression).
 */
@DisplayName("SQL logging scenarios (real proxy over H2)")
class SqlLoggingScenariosTest {

  private static final String JWT =
      "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ4In0.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";

  private JdbcDataSource h2;
  private Logger sqlLogger;
  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void setUp() throws Exception {
    h2 = new JdbcDataSource();
    h2.setURL("jdbc:h2:mem:scenarios-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
    h2.setUser("sa");
    try (Connection c = h2.getConnection();
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

  private DataSource proxy(DebugProperties properties) {
    SensitiveDataMasker masker = new SensitiveDataMasker(properties.getMasking());
    MaskingSqlLoggingListener listener =
        new MaskingSqlLoggingListener(masker, new DebugRuntimeState(), properties.getSql());
    return ProxyDataSourceBuilder.create(h2).name("scenarios").listener(listener).build();
  }

  private String firstLogContaining(String needle) {
    return new ArrayList<>(appender.list)
        .stream()
            .map(ILoggingEvent::getFormattedMessage)
            .filter(m -> m.contains(needle))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no SQL log line containing: " + needle));
  }

  @Test
  @DisplayName("masks secrets in every row of a batch insert")
  void batchInsertMasksEveryRow() throws Exception {
    DataSource ds = proxy(new DebugProperties());
    try (Connection c = ds.getConnection();
        PreparedStatement ps =
            c.prepareStatement(
                "insert into users (user_id, user_email, user_password, token) values (?, ?, ?, ?)")) {
      ps.setString(1, "a");
      ps.setString(2, "a@x.io");
      ps.setString(3, "pw-alpha");
      ps.setString(4, JWT);
      ps.addBatch();
      ps.setString(1, "b");
      ps.setString(2, "b@x.io");
      ps.setString(3, "pw-bravo");
      ps.setString(4, JWT);
      ps.addBatch();
      ps.executeBatch();
    }

    String logged = firstLogContaining("insert into users");
    assertThat(logged).doesNotContain("pw-alpha").doesNotContain("pw-bravo").doesNotContain("eyJ");
    // Two rows rendered, both masked.
    assertThat(logged.split("user_password=\\*\\*\\*MASKED\\*\\*\\*", -1)).hasSizeGreaterThan(2);
  }

  @Test
  @DisplayName("masks the sensitive column of an update")
  void updateMasksSensitiveColumn() throws Exception {
    DataSource ds = proxy(new DebugProperties());
    try (Connection c = ds.getConnection();
        PreparedStatement ps =
            c.prepareStatement("update users set user_password = ? where user_id = ?")) {
      ps.setString(1, "new-secret-value");
      ps.setString(2, "a");
      ps.executeUpdate();
    }

    String logged = firstLogContaining("update users");
    assertThat(logged).contains("user_password=***MASKED***").doesNotContain("new-secret-value");
  }

  @Test
  @DisplayName("masks PII by value pattern even on a non-sensitive column")
  void valuePatternMasksPiiParameter() throws Exception {
    DataSource ds = proxy(new DebugProperties());
    try (Connection c = ds.getConnection();
        PreparedStatement ps =
            c.prepareStatement("select user_id from users where user_email = ?")) {
      ps.setString(1, "victim@example.com");
      ps.executeQuery().close();
    }

    String logged = firstLogContaining("where user_email");
    assertThat(logged).doesNotContain("victim@example.com").contains("***MASKED***");
  }

  @Test
  @DisplayName("does not log statements faster than the slow-query threshold")
  void slowQueryThresholdFiltersFastStatements() throws Exception {
    DebugProperties properties = new DebugProperties();
    properties.getSql().setSlowQueryThreshold(Duration.ofSeconds(30));
    DataSource ds = proxy(properties);

    try (Connection c = ds.getConnection();
        PreparedStatement ps = c.prepareStatement("select user_id from users where user_id = ?")) {
      ps.setString(1, "a");
      ps.executeQuery().close();
    }

    assertThat(appender.list).as("fast statement must not be logged").isEmpty();
  }

  @Test
  @DisplayName("truncates long parameter values")
  void truncatesLongParameters() throws Exception {
    DebugProperties properties = new DebugProperties();
    properties.getSql().setMaxParameterLength(5);
    DataSource ds = proxy(properties);

    String longId = "0123456789ABCDEF";
    try (Connection c = ds.getConnection();
        PreparedStatement ps =
            c.prepareStatement(
                "insert into users (user_id, user_email, user_password, token) values (?, ?, ?, ?)")) {
      ps.setString(1, longId);
      ps.setString(2, "n@x.io");
      ps.setString(3, "p");
      ps.setString(4, "t");
      ps.executeUpdate();
    }

    String logged = firstLogContaining("insert into users");
    assertThat(logged).contains("01234...(16 chars)").doesNotContain(longId);
  }

  @Test
  @DisplayName("masking can be disabled by configuration (and then secrets are not masked)")
  void maskingDisabledLetsValuesThrough() throws Exception {
    DebugProperties properties = new DebugProperties();
    properties.getMasking().setEnabled(false);
    DataSource ds = proxy(properties);

    try (Connection c = ds.getConnection();
        PreparedStatement ps =
            c.prepareStatement(
                "insert into users (user_id, user_email, user_password, token) values (?, ?, ?, ?)")) {
      ps.setString(1, "a");
      ps.setString(2, "a@x.io");
      ps.setString(3, "visible-password");
      ps.setString(4, "t");
      ps.executeUpdate();
    }

    String logged = firstLogContaining("insert into users");
    assertThat(logged).contains("visible-password").doesNotContain("***MASKED***");
  }

  @Test
  @DisplayName("deny-by-default: mask-all hides every value, keeping only column and type")
  void maskAllHidesEveryValue() throws Exception {
    DebugProperties properties = new DebugProperties();
    properties.getMasking().setMaskAllParameters(true);
    DataSource ds = proxy(properties);

    try (Connection c = ds.getConnection();
        PreparedStatement ps =
            c.prepareStatement(
                "insert into users (user_id, user_email, user_password, token) values (?, ?, ?, ?)")) {
      ps.setString(1, "plain-looking-id");
      ps.setString(2, "victim@example.com");
      ps.setString(3, "hunter2");
      ps.setString(4, "anything");
      ps.executeUpdate();
    }

    String logged = firstLogContaining("insert into users");
    // No parameter value appears in clear, even the innocuous-looking, non-sensitive column.
    assertThat(logged)
        .doesNotContain("plain-looking-id")
        .doesNotContain("victim@example.com")
        .doesNotContain("hunter2")
        .doesNotContain("anything");
    // Column name and type are kept.
    assertThat(logged).contains("user_id=<String>***MASKED***");
  }

  @Test
  @DisplayName("mask-all masks an inline string literal in a native statement (no pattern needed)")
  void maskAllMasksInlineStatementLiteral() throws Exception {
    DebugProperties properties = new DebugProperties();
    properties.getMasking().setMaskAllParameters(true);
    DataSource ds = proxy(properties);

    // 'TopSecretValue123' matches no value pattern; only the deny-by-default literal masking
    // catches
    // it. It must not appear in the logged statement text.
    try (Connection c = ds.getConnection();
        Statement s = c.createStatement()) {
      s.execute("select user_id from users where user_id = 'TopSecretValue123'");
    }

    String logged = firstLogContaining("select user_id from users");
    assertThat(logged).doesNotContain("TopSecretValue123");
  }

  @Test
  @DisplayName("masks a long secret then truncates for display (no prefix leak)")
  void masksBeforeTruncating() throws Exception {
    DebugProperties properties = new DebugProperties();
    // Tiny display window: a JWT is longer and its header prefix has no dot, so masking *before*
    // truncating is what prevents that prefix from leaking.
    properties.getSql().setMaxParameterLength(20);
    DataSource ds = proxy(properties);

    try (Connection c = ds.getConnection();
        PreparedStatement ps = c.prepareStatement("select user_id from users where user_id = ?")) {
      ps.setString(1, JWT);
      ps.executeQuery().close();
    }

    String logged = firstLogContaining("where user_id");
    assertThat(logged).doesNotContain("eyJ");
  }

  @Test
  @DisplayName("the proxy does not change query results (no regression)")
  void proxyPreservesResults() throws Exception {
    DataSource ds = proxy(new DebugProperties());
    try (Connection c = ds.getConnection()) {
      try (PreparedStatement ps =
          c.prepareStatement(
              "insert into users (user_id, user_email, user_password, token) values (?, ?, ?, ?)")) {
        ps.setString(1, "rt-1");
        ps.setString(2, "rt@x.io");
        ps.setString(3, "pw");
        ps.setString(4, "tok");
        ps.executeUpdate();
      }
      try (PreparedStatement ps =
          c.prepareStatement("select user_email from users where user_id = ?")) {
        ps.setString(1, "rt-1");
        try (ResultSet rs = ps.executeQuery()) {
          assertThat(rs.next()).isTrue();
          assertThat(rs.getString("user_email")).isEqualTo("rt@x.io");
        }
      }
    }
  }
}
