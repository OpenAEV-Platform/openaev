package io.openaev.config;

import static org.junit.jupiter.api.Assertions.fail;

import io.openaev.config.WriteAttrDetectorRecorder.Violation;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * {@code [WRITEATTR-STALE]} marker at JVM shutdown, and the produced and not-produced counts are
 * written to {@value #WAIVER_REPORT_FILE} next to the surefire reports for the CI shadow summary.
 * The console marker reaches a single-JVM run's log only: in a sharded run, surefire no longer
 * relays the fork's stdout when the hook runs, so the file is the channel that reaches CI. Either
 * way a shard sees only its own classes, so a line stale in one shard may be produced in another.
 *
 * <p>Three kinds of entry frame reach the key. A production frame captured when the write was asked
 * for, or read off the stack of a synchronous write. An {@code unattributed(<test>)} key for a
 * flush-time write nobody captured whose flush stack holds no production frame (a dirty-checked
 * update, a collection of an owner that was loaded rather than persisted): the detector cannot say
 * who asked for it, so it names the test that flushed it instead of dropping it. And null, for a
 * synchronous write issued by test code itself, which is the only shape auto-waived.
 */
public class WriteAttrGateExtension implements BeforeEachCallback, AfterEachCallback {

  private static final boolean ENABLED =
      "on".equals(System.getProperty("openaev.writeattr.detector"));
  private static final String BASELINE_RESOURCE = "/writeattr-baseline.txt";

  /** Per-JVM waiver report, read by the CI shadow summary next to the surefire reports. */
  static final String WAIVER_REPORT_FILE = "writeattr-waivers.txt";

  private static final Set<String> WAIVED = loadBaseline();
  private static final Set<String> PRODUCED = ConcurrentHashMap.newKeySet();
  private static volatile List<String> lastKeyed = List.of();
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
    lastKeyed = keyed(violations);
    lastKeyed.forEach(PRODUCED::add);
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

  /** Whether the gate is armed in this JVM ({@code -Dopenaev.writeattr.detector=on}). */
  static boolean isEnabled() {
    return ENABLED;
  }

  /**
   * The keyed signatures of the last test the gate closed, for tests that pin what the gate did or
   * did not see at the end of the previous test in the same class.
   */
  static List<String> lastKeyed() {
    return lastKeyed;
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
                () -> {
                  staleWaivers().forEach(sig -> System.out.println("[WRITEATTR-STALE] " + sig));
                  writeWaiverReport();
                },
                "writeattr-stale-report"));
  }

  /**
   * Writes which baseline lines this JVM produced and which it did not next to the surefire
   * reports, where the CI shadow summary reads. The console markers above never reach that summary:
   * surefire keeps the fork's stdout out of its report files. Best effort, never throws; a shard
   * sees only its own classes, so the summary states the count per shard, and a line is stale only
   * when no shard produced it.
   */
  static void writeWaiverReport() {
    try {
      Path reports = Path.of(System.getProperty("basedir", ""), "target", "surefire-reports");
      if (!Files.isDirectory(reports)) {
        return;
      }
      Set<String> stale = staleWaivers();
      StringBuilder out = new StringBuilder();
      out.append("# write-attribution waivers: ")
          .append(WAIVED.size() - stale.size())
          .append(" of ")
          .append(WAIVED.size())
          .append(" produced in this JVM\n");
      stale.stream().sorted().forEach(sig -> out.append("not-produced ").append(sig).append('\n'));
      Files.writeString(
          reports.resolve(WAIVER_REPORT_FILE), out.toString(), StandardCharsets.UTF_8);
    } catch (IOException | RuntimeException e) {
      // nothing readable can act on a shutdown-time failure
    }
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
