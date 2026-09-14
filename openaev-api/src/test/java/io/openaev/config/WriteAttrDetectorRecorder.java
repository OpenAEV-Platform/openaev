package io.openaev.config;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test-only collector for the write-attribution detector, the write-side twin of {@link
 * FailClosedAccessRecorder}. Records every INSERT/UPDATE that stamped a {@code tenant_id} outside
 * the transaction's v2 scope: the row lands in a tenant the request or job was not scoped to (a
 * wrong-tenant write). The datasource-proxy detector listener writes here; a test reads {@link
 * #violations()} between {@link #start()} and {@link #stop()}.
 *
 * <p>The signal is the asymmetry the {@code app.current_tenants} scope defines, not a
 * default-tenant write: {@code TxCtxArgumentResolver.fallbackSelector} deliberately scopes a
 * tenant-unaware multi-tenant caller to the default tenant, and that write is inside its scope. See
 * {@link WriteAttrDetectorListener}.
 */
public final class WriteAttrDetectorRecorder {

  /**
   * One recorded wrong-tenant write. {@code table}/{@code writtenTenant}/{@code scope} come from
   * the trigger; {@code caller} is the Java frame that emitted the statement (best effort).
   */
  public record Violation(String table, String writtenTenant, String scope, String caller) {}

  private static final List<Violation> VIOLATIONS = new CopyOnWriteArrayList<>();
  private static volatile boolean recording = false;

  private WriteAttrDetectorRecorder() {}

  public static void start() {
    VIOLATIONS.clear();
    recording = true;
  }

  public static void stop() {
    recording = false;
  }

  static void record(String table, String writtenTenant, String scope, String caller) {
    if (recording) {
      VIOLATIONS.add(new Violation(table, writtenTenant, scope, caller));
    }
  }

  public static List<Violation> violations() {
    return Collections.unmodifiableList(VIOLATIONS);
  }
}
