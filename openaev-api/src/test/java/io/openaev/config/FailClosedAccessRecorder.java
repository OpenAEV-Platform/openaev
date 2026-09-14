package io.openaev.config;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test-only collector for the runtime fail-closed detector (WS1). Records every statement that
 * touched a v2-active table while the tenant scope GUC ({@code app.current_tenants}) was empty - a
 * real code path reached an active table with no scope, which in production silently reads zero
 * rows (fail-closed). The datasource-proxy detector listener writes here; a test reads {@link
 * #violations()} between {@link #start()} and {@link #stop()}.
 *
 * <p>Static collector because the datasource-proxy builds a single listener instance, mirroring
 * {@link CapturingStatementInspector}. Capture is scoped to the unit under test (not fixture setup)
 * with start/stop.
 */
public final class FailClosedAccessRecorder {

  /**
   * One recorded unscoped access to an active table. {@code caller} locates the emitting call site.
   */
  public record Violation(String activeTables, String caller, String sql) {}

  private static final List<Violation> VIOLATIONS = new CopyOnWriteArrayList<>();
  private static volatile boolean recording = false;

  private FailClosedAccessRecorder() {}

  public static void start() {
    VIOLATIONS.clear();
    recording = true;
  }

  public static void stop() {
    recording = false;
  }

  static void record(String activeTables, String caller, String sql) {
    if (recording) {
      VIOLATIONS.add(new Violation(activeTables, caller, sql));
    }
  }

  public static List<Violation> violations() {
    return Collections.unmodifiableList(VIOLATIONS);
  }
}
