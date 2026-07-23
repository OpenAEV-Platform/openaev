package io.openaev.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

public class PoolExhaustionWatchdogTest {

  private ListAppender<ILoggingEvent> appender;
  private Logger watchdogLogger;

  @BeforeEach
  void setUpLogCapture() {
    watchdogLogger = (Logger) LoggerFactory.getLogger(PoolExhaustionWatchdog.class);
    appender = new ListAppender<>();
    appender.start();
    watchdogLogger.addAppender(appender);
  }

  @AfterEach
  void tearDownLogCapture() {
    watchdogLogger.detachAppender(appender);
    appender.stop();
  }

  private List<ILoggingEvent> errorEvents() {
    return appender.list.stream().filter(e -> e.getLevel() == Level.ERROR).toList();
  }

  private static PoolExhaustionWatchdog watchdogFor(HikariPoolMXBean poolBean) throws SQLException {
    DataSource dataSource = mock(DataSource.class);
    HikariDataSource hikariDataSource = mock(HikariDataSource.class);
    when(dataSource.isWrapperFor(HikariDataSource.class)).thenReturn(true);
    when(dataSource.unwrap(HikariDataSource.class)).thenReturn(hikariDataSource);
    when(hikariDataSource.getHikariPoolMXBean()).thenReturn(poolBean);
    return new PoolExhaustionWatchdog(dataSource);
  }

  private static void saturate(HikariPoolMXBean poolBean) {
    when(poolBean.getTotalConnections()).thenReturn(20);
    when(poolBean.getActiveConnections()).thenReturn(20);
    when(poolBean.getIdleConnections()).thenReturn(0);
    when(poolBean.getThreadsAwaitingConnection()).thenReturn(31);
  }

  private static void recover(HikariPoolMXBean poolBean) {
    when(poolBean.getTotalConnections()).thenReturn(20);
    when(poolBean.getActiveConnections()).thenReturn(3);
    when(poolBean.getIdleConnections()).thenReturn(17);
    when(poolBean.getThreadsAwaitingConnection()).thenReturn(0);
  }

  @Test
  @DisplayName("sustained saturation dumps once and respects the cooldown")
  void given_sustained_saturation_should_dump_once_with_cooldown() throws SQLException {
    HikariPoolMXBean poolBean = mock(HikariPoolMXBean.class);
    PoolExhaustionWatchdog watchdog = watchdogFor(poolBean);
    saturate(poolBean);

    // First saturated sample: below the persistence threshold, no dump yet
    watchdog.sample();
    assertThat(errorEvents()).isEmpty();

    // Second consecutive saturated sample: one ERROR with the pool stats and the dump
    watchdog.sample();
    assertThat(errorEvents()).hasSize(1);
    String message = errorEvents().get(0).getFormattedMessage();
    assertThat(message).contains("total=20, active=20, idle=0, awaiting=31");
    assertThat(message).contains("OTHER THREADS");

    // Still saturated within the cooldown window: no additional dump
    watchdog.sample();
    watchdog.sample();
    assertThat(errorEvents()).hasSize(1);
  }

  @Test
  @DisplayName("a saturation blip between recoveries never dumps")
  void given_saturation_blip_should_not_dump() throws SQLException {
    HikariPoolMXBean poolBean = mock(HikariPoolMXBean.class);
    PoolExhaustionWatchdog watchdog = watchdogFor(poolBean);

    saturate(poolBean);
    watchdog.sample();
    // Recovery resets the consecutive-sample counter...
    recover(poolBean);
    watchdog.sample();
    // ...so a new single saturated sample is again below the persistence threshold
    saturate(poolBean);
    watchdog.sample();

    assertThat(errorEvents()).isEmpty();
  }

  @Test
  @DisplayName("non-Hikari datasource disables the watchdog permanently without probing again")
  void given_non_hikari_datasource_should_disable_permanently() throws SQLException {
    DataSource dataSource = mock(DataSource.class);
    when(dataSource.isWrapperFor(HikariDataSource.class)).thenReturn(false);
    PoolExhaustionWatchdog watchdog = new PoolExhaustionWatchdog(dataSource);

    watchdog.sample();
    watchdog.sample();

    // Probed once, then disabled: no repeated unwrap attempts, no log noise
    verify(dataSource, times(1)).isWrapperFor(HikariDataSource.class);
    assertThat(errorEvents()).isEmpty();
  }

  @Test
  @DisplayName("datasource unwrap failure disables the watchdog with a single warning")
  void given_unwrap_failure_should_disable_with_warning() throws SQLException {
    DataSource dataSource = mock(DataSource.class);
    when(dataSource.isWrapperFor(HikariDataSource.class)).thenThrow(new SQLException("no wrapper"));
    PoolExhaustionWatchdog watchdog = new PoolExhaustionWatchdog(dataSource);

    watchdog.sample();
    watchdog.sample();

    verify(dataSource, times(1)).isWrapperFor(HikariDataSource.class);
    assertThat(appender.list.stream().filter(e -> e.getLevel() == Level.WARN).toList()).hasSize(1);
    assertThat(errorEvents()).isEmpty();
  }

  private static StackTraceElement frame(String className) {
    return new StackTraceElement(className, "method", "File.java", 42);
  }

  private static Thread thread(String name) {
    // Never started: state NEW, only used as a dump key
    return new Thread(() -> {}, name);
  }

  @Test
  @DisplayName("threads inside JDBC frames are dumped with a full stack as suspected holders")
  void given_thread_inside_postgres_driver_should_be_flagged_as_holder() {
    Map<Thread, StackTraceElement[]> stacks =
        Map.of(
            thread("http-nio-8080-exec-1"),
            new StackTraceElement[] {
              frame("java.net.SocketInputStream"),
              frame("org.postgresql.core.v3.QueryExecutorImpl"),
              frame("org.hibernate.engine.jdbc.internal.StatementPreparerImpl"),
              frame("io.openaev.service.SomeService"),
            },
            thread("idle-worker"),
            new StackTraceElement[] {frame("java.lang.Object")});

    String dump = PoolExhaustionWatchdog.buildThreadDump(stacks);

    assertThat(dump).contains("1 thread(s) inside JDBC/Hibernate/Hikari frames");
    assertThat(dump).contains("--- HOLDER \"http-nio-8080-exec-1\"");
    // The holder's application frame is visible so the root cause is directly readable
    assertThat(dump).contains("io.openaev.service.SomeService");
    // The idle thread is summarized on a single line, not dumped in full
    assertThat(dump).contains("--- OTHER THREADS (1)");
    // Summarized entries are disambiguated with the thread id (names are not unique)
    assertThat(dump).containsPattern("\"idle-worker #\\d+\"");
  }

  @Test
  @DisplayName("threads without JDBC frames are only summarized")
  void given_no_jdbc_thread_should_report_zero_holders() {
    Map<Thread, StackTraceElement[]> stacks =
        Map.of(
            thread("scheduler-1"),
            new StackTraceElement[] {frame("java.util.concurrent.locks.LockSupport")});

    String dump = PoolExhaustionWatchdog.buildThreadDump(stacks);

    assertThat(dump).contains("0 thread(s) inside JDBC/Hibernate/Hikari frames");
    assertThat(dump).doesNotContain("--- HOLDER");
  }
}
