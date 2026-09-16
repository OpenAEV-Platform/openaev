package io.openaev.debug;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import javax.sql.DataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Drives the {@link OrmInsightFilter} with a real datasource proxy over H2 and a mock filter chain
 * that runs SQL inside the filtered request, so the N+1 detection and the per-request summary are
 * exercised through the same path used at runtime.
 */
@DisplayName("OrmInsightFilter (N+1 detection over a real proxy)")
class OrmInsightFilterTest {

  private JdbcDataSource h2;
  private DataSource proxy;
  private OrmInsightFilter filter;
  private Logger ormLogger;
  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void setUp() throws Exception {
    h2 = new JdbcDataSource();
    h2.setURL("jdbc:h2:mem:orm-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
    h2.setUser("sa");
    try (Connection c = h2.getConnection();
        Statement s = c.createStatement()) {
      s.execute("create table users (user_id varchar, user_email varchar)");
    }

    DebugProperties properties = new DebugProperties();
    SensitiveDataMasker masker = new SensitiveDataMasker(properties.getMasking());
    MaskingSqlLoggingListener listener =
        new MaskingSqlLoggingListener(masker, new DebugRuntimeState(), properties.getSql());
    proxy = ProxyDataSourceBuilder.create(h2).name("orm").listener(listener).build();
    DebugUserSource userSource =
        new DebugUserSource() {
          @Override
          public String currentUser() {
            return "u-42";
          }
        };
    filter = new OrmInsightFilter(masker, new DebugRuntimeState(), userSource);

    ormLogger = (Logger) LoggerFactory.getLogger("io.openaev.debug.orm");
    ormLogger.setLevel(Level.INFO);
    appender = new ListAppender<>();
    appender.start();
    ormLogger.addAppender(appender);
  }

  @AfterEach
  void tearDown() {
    ormLogger.detachAppender(appender);
    OrmInsightContext.clear();
  }

  private void runThroughFilter(String method, String uri, Runnable work) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
    MockHttpServletResponse response = new MockHttpServletResponse();
    HttpServlet servlet =
        new HttpServlet() {
          @Override
          protected void service(HttpServletRequest req, HttpServletResponse res) {
            work.run();
          }
        };
    filter.doFilter(request, response, new MockFilterChain(servlet));
  }

  private void exec(String sql, String param) {
    try (Connection c = proxy.getConnection();
        PreparedStatement ps = c.prepareStatement(sql)) {
      if (param != null) {
        ps.setString(1, param);
      }
      ps.execute();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private String summary() {
    return new ArrayList<>(appender.list)
        .stream()
            .map(ILoggingEvent::getFormattedMessage)
            .filter(m -> m.startsWith("ORM "))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no ORM summary logged"));
  }

  private Level summaryLevel() {
    return new ArrayList<>(appender.list)
        .stream()
            .filter(e -> e.getFormattedMessage().startsWith("ORM "))
            .map(ILoggingEvent::getLevel)
            .findFirst()
            .orElseThrow();
  }

  @Test
  @DisplayName("flags an N+1: same SELECT repeated above the threshold")
  void detectsNPlusOne() throws Exception {
    runThroughFilter(
        "GET",
        "/api/exercises/1",
        () -> {
          exec("select user_email from users where user_id = ?", "x"); // the "1" query
          for (int i = 0; i < 12; i++) {
            exec("select user_id from users where user_email = ?", "u" + i); // the "N" query
          }
        });

    String summary = summary();
    assertThat(summaryLevel()).isEqualTo(Level.WARN);
    assertThat(summary)
        .contains("GET /api/exercises/1")
        .contains("13 queries")
        .contains("N+1 SUSPECTED")
        .contains("where user_email = ?")
        .contains("executed 12x");
  }

  @Test
  @DisplayName("clean request: one INFO summary, no warning")
  void cleanRequest() throws Exception {
    runThroughFilter(
        "GET",
        "/api/tags",
        () -> {
          exec("select user_id from users where user_id = ?", "a");
          exec("select user_email from users where user_id = ?", "b");
        });

    assertThat(summaryLevel()).isEqualTo(Level.INFO);
    assertThat(summary()).contains("GET /api/tags").contains("2 queries");
  }

  @Test
  @DisplayName("chatty request: total query count over the threshold is flagged")
  void chattyRequest() throws Exception {
    runThroughFilter(
        "GET",
        "/api/dashboard",
        () -> {
          for (int i = 0; i < 55; i++) {
            exec("select " + i, null); // 55 distinct statements, none individually repeated
          }
        });

    assertThat(summaryLevel()).isEqualTo(Level.WARN);
    assertThat(summary()).contains("CHATTY REQUEST").contains("55 queries");
  }

  @Test
  @DisplayName("masks secrets in the offender SQL printed in the summary")
  void masksOffenderSql() throws Exception {
    runThroughFilter(
        "GET",
        "/api/x",
        () -> {
          for (int i = 0; i < 12; i++) {
            exec("select user_id from users where user_email = 'victim@example.com'", null);
          }
        });

    assertThat(summary()).contains("N+1 SUSPECTED").doesNotContain("victim@example.com");
  }

  @Test
  @DisplayName("the summary names the caller (user=...) on both the INFO and WARN paths")
  void summaryCarriesCaller() throws Exception {
    runThroughFilter("GET", "/api/tags", () -> exec("select 1", null)); // clean -> INFO
    assertThat(summaryLevel()).isEqualTo(Level.INFO);
    assertThat(summary()).contains("user=u-42");

    appender.list.clear();
    runThroughFilter(
        "POST",
        "/api/injectors",
        () -> {
          for (int i = 0; i < 12; i++) {
            exec("select user_id from users where user_email = ?", "u" + i);
          }
        }); // N+1 -> WARN
    assertThat(summaryLevel()).isEqualTo(Level.WARN);
    assertThat(summary()).contains("user=u-42");
  }

  @Test
  @DisplayName("the INFO summary is fully rendered (no unresolved {} placeholders)")
  void infoSummaryIsPreFormatted() throws Exception {
    runThroughFilter("GET", "/api/tags", () -> exec("select 1", null));

    // The message itself carries the resolved values, so a JSON encoder that keeps the raw pattern
    // still shows real numbers instead of "ORM {}: {} queries ...".
    ILoggingEvent event =
        new ArrayList<>(appender.list)
            .stream()
                .filter(e -> e.getFormattedMessage().startsWith("ORM "))
                .findFirst()
                .orElseThrow();
    assertThat(event.getMessage()).doesNotContain("{}").contains("GET /api/tags", "1 queries");
    assertThat(event.getArgumentArray()).isNullOrEmpty();
  }

  @Test
  @DisplayName("clears the per-request context after the request")
  void clearsContext() throws Exception {
    runThroughFilter("GET", "/api/x", () -> exec("select 1", null));
    assertThat(OrmInsightContext.current()).isNull();
  }

  @Test
  @DisplayName("a batch counts as one execution, not as N (not an N+1)")
  void batchIsNotNPlusOne() throws Exception {
    runThroughFilter(
        "POST",
        "/api/import",
        () -> {
          try (Connection c = proxy.getConnection();
              PreparedStatement ps =
                  c.prepareStatement("insert into users (user_id, user_email) values (?, ?)")) {
            for (int i = 0; i < 30; i++) {
              ps.setString(1, "id-" + i);
              ps.setString(2, "u" + i + "@x.io");
              ps.addBatch();
            }
            ps.executeBatch();
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });

    // One batched execution -> not flagged as N+1, summary stays INFO.
    assertThat(summaryLevel()).isEqualTo(Level.INFO);
    assertThat(summary()).doesNotContain("N+1");
  }
}
