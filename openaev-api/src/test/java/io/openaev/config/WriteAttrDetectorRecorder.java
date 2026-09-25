package io.openaev.config;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test-only collector for the write-attribution detector, the write-side twin of {@link
 * FailClosedAccessRecorder}. Records every INSERT/UPDATE that stamped a {@code tenant_id} outside
 * the transaction's v2 scope, or a null {@code tenant_id} on a strict table: the row lands in a
 * tenant the request or job was not scoped to (a wrong-tenant write). The datasource-proxy detector
 * listener writes here; a test reads {@link #violations()} between {@link #start()} and {@link
 * #stop()}.
 *
 * <p>The signal is the asymmetry the {@code app.current_tenants} scope defines, not a
 * default-tenant write per se: {@code TxCtxArgumentResolver.fallbackSelector} deliberately scopes a
 * tenant-unaware multi-tenant caller to the default tenant, and that write is inside its scope. See
 * {@link WriteAttrDetectorListener}.
 *
 * <p>This recorder is raw: it keeps every violation the trigger raised, test-driven or production.
 * The test/fixture auto-waive and the baseline diff are the gate's job ({@link
 * WriteAttrGateExtension}), exactly as the fail-closed recorder is raw and its gate filters.
 */
public final class WriteAttrDetectorRecorder {

  /**
   * One recorded wrong-tenant write. {@code table}/{@code writtenTenant}/{@code scope}/{@code
   * relation} come from the trigger and are always exact. {@code entryFrame} is the outermost
   * production frame on the stack (the waiver key, null when the write is test-driven with no
   * production frame); {@code innermostCaller} is the nearest frame that emitted the statement,
   * kept for triage, best effort for a lazily flushed insert.
   */
  public record Violation(
      String table,
      String writtenTenant,
      String scope,
      WriteAttrSignature.Relation relation,
      String entryFrame,
      String innermostCaller) {}

  private static final List<Violation> VIOLATIONS = new CopyOnWriteArrayList<>();
  private static volatile boolean recording = false;

  private WriteAttrDetectorRecorder() {}

  public static void start() {
    VIOLATIONS.clear();
    WriteAttrEntryFrames.clear();
    recording = true;
  }

  public static void stop() {
    recording = false;
    WriteAttrEntryFrames.clear();
  }

  /**
   * Whether a test is currently recording. The Hibernate attribution listeners capture only while
   * this is true: a write the trigger can observe has flushed during the test body (a rollback does
   * not flush pending inserts), which is inside the recording window, so nothing is missed and
   * context startup pays no cost.
   */
  public static boolean isRecording() {
    return recording;
  }

  static void record(Violation violation) {
    if (recording) {
      VIOLATIONS.add(violation);
    }
  }

  public static List<Violation> violations() {
    return Collections.unmodifiableList(VIOLATIONS);
  }
}
