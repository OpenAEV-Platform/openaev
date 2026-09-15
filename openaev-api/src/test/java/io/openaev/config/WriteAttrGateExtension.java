package io.openaev.config;

import static org.junit.jupiter.api.Assertions.fail;

import io.openaev.config.WriteAttrDetectorRecorder.Violation;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Write-attribution gate (G1), the write-side twin of {@link FailClosedGateExtension}.
 * Auto-registered for every test (JUnit extension autodetection), active only under {@code
 * -Dopenaev.writeattr.detector=on} - so normal CI is untouched and only an opt-in run pays the
 * cost. It records each test's wrong-tenant writes (via {@link WriteAttrDetectorListener}, wired
 * suite-wide by {@link WriteAttrDetectorContextCustomizerFactory}) and fails the test if a NEW one
 * originates from a production entry point.
 *
 * <p>Waiving is a baseline diff, per-test so it is shard-safe. A signature is {@code table} + the
 * written tenant's relation to the scope + the outermost production entry frame. A write with no
 * production entry frame is test-driven and is auto-waived (a test writing where it likes is never
 * a production bug). A write from a production entry frame must be listed in {@code
 * writeattr-baseline.txt}; anything else fails, i.e. a genuinely new wrong-tenant write as tables
 * are onboarded.
 *
 * <p>Stale waivers are reported: a baseline line never produced across the run is printed with a
 * {@code [WRITEATTR-STALE]} marker at JVM shutdown. That is reliable only in an unsharded run; a
 * shard sees only its own classes, so a line stale in one shard may be produced in another.
 */
public class WriteAttrGateExtension implements BeforeEachCallback, AfterEachCallback {

  private static final boolean ENABLED =
      "on".equals(System.getProperty("openaev.writeattr.detector"));
  private static final String BASELINE_RESOURCE = "/writeattr-baseline.txt";
  private static final Set<String> WAIVED = loadBaseline();
  private static final Set<String> PRODUCED = ConcurrentHashMap.newKeySet();
  private static final AtomicBoolean STALE_HOOK_REGISTERED = new AtomicBoolean(false);

  public WriteAttrGateExtension() {
    if (ENABLED) {
      registerStaleReport();
    }
  }

  @Override
  public void beforeEach(ExtensionContext context) {
    if (ENABLED) {
      WriteAttrDetectorRecorder.start();
    }
  }

  @Override
  public void afterEach(ExtensionContext context) {
    if (!ENABLED) {
      return;
    }
    WriteAttrDetectorRecorder.stop();
    List<Violation> violations = WriteAttrDetectorRecorder.violations();
    keyed(violations).forEach(PRODUCED::add);
    List<String> offending = offendingSignatures(violations);
    if (!offending.isEmpty()) {
      fail(
          "Write-attribution: production code wrote a tenant_id outside the request or job scope "
              + "(a wrong-tenant write, or a null tenant on a strict table):\n  "
              + String.join("\n  ", offending)
              + "\nAttribute the write from the request's write scope (an HTTP TxCtx selector, or the "
              + "tenant-scoped primitive in background code). If it is intentional and safe, add the "
              + "signature to writeattr-baseline.txt with a reason.");
    }
  }

  /**
   * The distinct signatures of writes from a production entry frame that are not in the frozen
   * baseline - the gate fails on a non-empty result.
   */
  static List<String> offendingSignatures(Collection<Violation> violations) {
    return offendingSignatures(violations, WAIVED);
  }

  /**
   * Same decision against an explicit waiver set, so it is unit-tested without the JUnit lifecycle
   * and without depending on the frozen baseline. A violation with no production entry frame is
   * test-driven and is not keyed at all.
   */
  static List<String> offendingSignatures(Collection<Violation> violations, Set<String> waived) {
    return keyed(violations).stream().filter(sig -> !waived.contains(sig)).distinct().toList();
  }

  /** The signatures of the violations that carry a production entry frame. */
  private static List<String> keyed(Collection<Violation> violations) {
    return violations.stream()
        .filter(v -> v.entryFrame() != null)
        .map(v -> WriteAttrSignature.of(v.table(), v.relation(), v.entryFrame()))
        .distinct()
        .toList();
  }

  /** Baseline lines never produced across this run (unsharded runs only; see the class javadoc). */
  static Set<String> staleWaivers() {
    Set<String> stale = new HashSet<>(WAIVED);
    stale.removeAll(PRODUCED);
    return stale;
  }

  private void registerStaleReport() {
    if (!STALE_HOOK_REGISTERED.compareAndSet(false, true)) {
      return;
    }
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> staleWaivers().forEach(sig -> System.out.println("[WRITEATTR-STALE] " + sig)),
                "writeattr-stale-report"));
  }

  private static Set<String> loadBaseline() {
    Set<String> waived = new HashSet<>();
    try (InputStream in = WriteAttrGateExtension.class.getResourceAsStream(BASELINE_RESOURCE)) {
      if (in == null) {
        return waived;
      }
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          String trimmed = line.trim();
          if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
            waived.add(trimmed);
          }
        }
      }
    } catch (IOException e) {
      throw new IllegalStateException("cannot read " + BASELINE_RESOURCE, e);
    }
    return waived;
  }
}
