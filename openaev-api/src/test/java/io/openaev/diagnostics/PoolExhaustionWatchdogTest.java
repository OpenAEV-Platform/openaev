package io.openaev.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class PoolExhaustionWatchdogTest {

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
    assertThat(dump).contains("\"idle-worker\"");
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
