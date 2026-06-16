package io.openaev.config;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * Test-only inspector that records the SQL Hibernate actually emits without altering it. Registered
 * through {@code hibernate.session_factory.statement_inspector}; capture is off by default so the
 * SQL run at context start-up is ignored, and a test turns it on around the statements it wants.
 */
public class CapturingStatementInspector implements StatementInspector {

  private static final List<String> CAPTURED = new CopyOnWriteArrayList<>();
  private static volatile boolean capturing = false;

  static void start() {
    CAPTURED.clear();
    capturing = true;
  }

  static void stop() {
    capturing = false;
  }

  static List<String> captured() {
    return Collections.unmodifiableList(CAPTURED);
  }

  @Override
  public String inspect(String sql) {
    if (capturing && sql != null) {
      CAPTURED.add(sql);
    }
    return sql;
  }
}
