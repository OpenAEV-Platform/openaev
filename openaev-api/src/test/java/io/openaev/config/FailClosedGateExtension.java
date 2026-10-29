package io.openaev.config;

import static org.junit.jupiter.api.Assertions.fail;

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
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * WS1 fail-closed gate. Auto-registered for every test (JUnit extension autodetection), active only
 * under {@code -Dopenaev.failclosed.detector=on} - so normal CI is untouched and only an opt-in run
 * pays the cost. It records each test's fail-closed reads (via {@link FailClosedDetectorListener},
 * wired suite-wide by {@link FailClosedDetectorContextCustomizerFactory}) and fails the test if a
 * NEW one originates from production code.
 *
 * <p>Waiving is a baseline diff, per-test so it is shard-safe. A read whose nearest caller is test
 * or fixture code is auto-waived (a test choosing not to scope is never a production bug). A read
 * from production code must be listed in {@code failclosed-baseline.txt} (by {@code class.method});
 * anything else fails, i.e. a genuinely new unscoped production path as tables are onboarded
 * (WS2/WS3).
 *
 * <p><strong>Shadow modes.</strong> A shadow run arms a much wider active-tables list than the one
 * the test classpath declares (which is none at all), so every production method reading a newly
 * gated table is absent from {@code failclosed-baseline.txt} by construction. Failing each test on
 * it reports the same handful of signatures over and over while destroying the other half of what
 * the shadow is for, namely whether the suite passes under activation. So when {@value
 * #SHADOW_MODE_PROPERTY} arms a mode, the per-test assertion goes inert: the gate accumulates the
 * signatures and writes them to {@value #SHADOW_REPORT_FILE} next to the surefire reports, each
 * marked {@value #NEW_PREFIX} or {@value #KNOWN_PREFIX} against <em>that mode's own</em> stored
 * list ({@code failclosed-shadow-prod.txt}, {@code failclosed-shadow-all.txt}). The CI summary
 * fails the shadow verdict on a new one, so the number a shadow run reports is "signatures new
 * since the list was last curated" and is comparable from one night to the next.
 *
 * <p>The three lists stay separate on purpose. {@code failclosed-baseline.txt} is the normal
 * pipeline's reference and carries the curated per-signature reasons; a mode list says only which
 * signatures that armed set is already known to produce. A signature waived in the baseline is
 * waived in every mode too, since the baseline filter runs first.
 */
public class FailClosedGateExtension implements BeforeEachCallback, AfterEachCallback {

  private static final boolean ENABLED =
      "on".equals(System.getProperty("openaev.failclosed.detector"));
  private static final String UNKNOWN_CALLER = FailClosedDetectorListener.UNKNOWN_CALLER;
  private static final String BASELINE_RESOURCE = "/failclosed-baseline.txt";
  private static final Set<String> WAIVED = loadSignatureList(BASELINE_RESOURCE);

  /**
   * System property carrying the armed shadow mode. Absent or blank means the normal pipeline,
   * where the gate asserts per test exactly as it always did. Set by the CI shadow runs ({@code
   * .github/actions/api-tests/action.yml}), pinned by {@link FailClosedShadowSummaryMarkerTest}.
   */
  static final String SHADOW_MODE_PROPERTY = "openaev.failclosed.shadow-mode";

  /** The modes a shadow run may arm. Each ships its own stored signature list. */
  static final Set<String> SHADOW_MODES = Set.of("shadow-prod", "shadow-all");

  /**
   * Per-JVM shadow report, read by the CI shadow summary next to the surefire reports. It is the
   * only channel that reaches CI under a shadow mode: the per-test assertion no longer fires, so
   * nothing lands in the surefire report text, and the listener's {@code [FAILCLOSED]} markers go
   * to the fork's stdout, which surefire does not relay. Same rule as the write-side gate's
   * reports.
   */
  static final String SHADOW_REPORT_FILE = "failclosed-shadow.txt";

  /**
   * Line prefix of a signature absent from the armed mode's list. The CI summary counts these lines
   * and fails the shadow verdict on a non-zero count, so the prefix is a contract; pinned by {@link
   * FailClosedShadowSummaryMarkerTest}.
   */
  static final String NEW_PREFIX = "new ";

  /** Line prefix of a signature the armed mode's list already covers. Reported, never failed. */
  static final String KNOWN_PREFIX = "known ";

  private static final String SHADOW_MODE = shadowMode(System.getProperty(SHADOW_MODE_PROPERTY));
  private static final Set<String> MODE_LIST =
      SHADOW_MODE.isEmpty() ? Set.of() : loadSignatureList(modeListResource(SHADOW_MODE));

  /**
   * Production signatures this JVM observed under a shadow mode, across every test it ran. Never
   * compared against {@link #WAIVED} a second time (they are already filtered out when they are
   * recorded) and never able to fail a test.
   */
  private static final Set<String> OBSERVED = ConcurrentHashMap.newKeySet();

  private static final AtomicBoolean SHADOW_HOOK_REGISTERED = new AtomicBoolean(false);

  public FailClosedGateExtension() {
    if (ENABLED && !SHADOW_MODE.isEmpty()) {
      registerShadowReport();
    }
  }

  @Override
  public void beforeEach(ExtensionContext context) {
    if (ENABLED) {
      FailClosedAccessRecorder.start();
    }
  }

  @Override
  public void afterEach(ExtensionContext context) {
    if (!ENABLED) {
      return;
    }
    FailClosedAccessRecorder.stop();
    List<String> offending = offendingSignatures(FailClosedAccessRecorder.violations());
    if (offending.isEmpty()) {
      return;
    }
    if (!failsPerTest(SHADOW_MODE)) {
      OBSERVED.addAll(offending);
      return;
    }
    fail(
        "Fail-closed: production code read an active tenant table with no scope (would return zero "
            + "rows in production):\n  "
            + String.join("\n  ", offending)
            + "\nScope the path (an HTTP TxCtx parameter, or the TenantScopedTransaction primitive "
            + "in background code). If it is intentional and safe, add the signature to "
            + "failclosed-baseline.txt with a reason.");
  }

  /**
   * Whether a non-empty offending set must fail the test. True only when no shadow mode is armed: a
   * shadow run reports its signature set and nothing else, because its armed table list makes every
   * newly gated read new against the baseline by construction. Extracted so both directions are
   * unit-tested without the JUnit lifecycle and without a system property.
   */
  static boolean failsPerTest(String shadowMode) {
    return shadowMode == null || shadowMode.isBlank();
  }

  /** The shadow mode armed in this JVM, empty when none. */
  static String armedShadowMode() {
    return SHADOW_MODE;
  }

  /**
   * Validates the raw property value. A blank value means no mode. An unrecognised one is refused
   * rather than read as "no mode armed": that would silently put the per-test assertion back in
   * front of a shadow run, which is the state this gate moved away from, and nothing downstream
   * would say so.
   */
  static String shadowMode(String raw) {
    if (raw == null || raw.isBlank()) {
      return "";
    }
    String mode = raw.trim();
    if (!SHADOW_MODES.contains(mode)) {
      throw new IllegalArgumentException(
          "unknown "
              + SHADOW_MODE_PROPERTY
              + ": '"
              + mode
              + "' (expected one of "
              + new TreeSet<>(SHADOW_MODES)
              + ", or nothing at all for the normal pipeline)");
    }
    return mode;
  }

  /** The classpath resource holding a mode's own stored signature list. */
  static String modeListResource(String mode) {
    return "/failclosed-" + mode + ".txt";
  }

  /** The observed signatures absent from the armed mode's own list, sorted. */
  static List<String> newSignatures(Collection<String> observed, Set<String> modeList) {
    return observed.stream().filter(sig -> !modeList.contains(sig)).distinct().sorted().toList();
  }

  /**
   * The content {@link #writeShadowReport()} writes, factored out so the format is unit-tested
   * without the shutdown hook or the filesystem: a header naming the armed mode and the two counts,
   * then one sorted line per observed signature, prefixed {@value #NEW_PREFIX} or {@value
   * #KNOWN_PREFIX}. Both kinds are listed, because a mode's list is curated by reading this file.
   */
  static String shadowReportContent(
      String mode, Collection<String> observed, Set<String> modeList) {
    List<String> fresh = newSignatures(observed, modeList);
    StringBuilder out = new StringBuilder();
    out.append("# fail-closed shadow ")
        .append(mode)
        .append(": ")
        .append(new HashSet<>(observed).size())
        .append(" production signatures observed, ")
        .append(fresh.size())
        .append(" new to this mode's list\n");
    new TreeSet<>(observed)
        .forEach(
            sig ->
                out.append(modeList.contains(sig) ? KNOWN_PREFIX : NEW_PREFIX)
                    .append(sig)
                    .append('\n'));
    return out.toString();
  }

  private void registerShadowReport() {
    if (!SHADOW_HOOK_REGISTERED.compareAndSet(false, true)) {
      return;
    }
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(FailClosedGateExtension::writeShadowReport, "failclosed-shadow-report"));
  }

  /**
   * Writes this JVM's observed signatures next to the surefire reports, where the CI shadow summary
   * reads them. Written even when empty: a missing file means the gate never ran, which the summary
   * reports as a broken instrument rather than as a pass. Best effort, never throws; a shard sees
   * only its own classes, so the summary states the count per shard and a mode's list is curated
   * from every shard together.
   */
  static void writeShadowReport() {
    try {
      Path reports = Path.of(System.getProperty("basedir", ""), "target", "surefire-reports");
      if (!Files.isDirectory(reports)) {
        return;
      }
      Files.writeString(
          reports.resolve(SHADOW_REPORT_FILE),
          shadowReportContent(SHADOW_MODE, new HashSet<>(OBSERVED), MODE_LIST),
          StandardCharsets.UTF_8);
    } catch (IOException | RuntimeException e) {
      // nothing readable can act on a shutdown-time failure
    }
  }

  /**
   * The distinct production call-site signatures that are not waived - the gate fails on a
   * non-empty result. Extracted so the decision is unit-tested directly, without the JUnit
   * lifecycle.
   */
  static List<String> offendingSignatures(
      Collection<FailClosedAccessRecorder.Violation> violations) {
    return violations.stream()
        .filter(v -> !UNKNOWN_CALLER.equals(v.caller()))
        .filter(v -> !isTestCaller(v.caller()))
        .map(v -> signature(v.caller()))
        .filter(sig -> !WAIVED.contains(sig))
        .distinct()
        .toList();
  }

  /**
   * The call site without its line number: {@code io.openaev.Foo.bar:42} -> {@code
   * io.openaev.Foo.bar}.
   */
  private static String signature(String caller) {
    int colon = caller.lastIndexOf(':');
    return colon < 0 ? caller : caller.substring(0, colon);
  }

  /**
   * True when the emitting frame is test or fixture code, which is never a production fail-closed
   * bug. Shares its rule with {@code WriteAttrStack.isTestFrame}, including why {@code .mockUser.}
   * and {@code *TestHelper} are markers rather than a bare {@code io.openaev.utils.} prefix (that
   * package also holds production utility classes under the same package name); see that method's
   * javadoc.
   */
  private static boolean isTestCaller(String caller) {
    String sig = signature(caller);
    int lastDot = sig.lastIndexOf('.');
    if (lastDot < 0) {
      return false;
    }
    String classFqn = sig.substring(0, lastDot);
    if (classFqn.contains(".fixtures.")
        || classFqn.contains(".utilstest.")
        || classFqn.contains(".composers.")
        || classFqn.contains(".mockUser.")) {
      return true;
    }
    String outer = classFqn.contains("$") ? classFqn.substring(0, classFqn.indexOf('$')) : classFqn;
    String simpleName = outer.substring(outer.lastIndexOf('.') + 1);
    return simpleName.endsWith("Test")
        || simpleName.endsWith("IT")
        || simpleName.endsWith("Benchmark")
        || simpleName.endsWith("TestHelper");
  }

  /**
   * One signature per line, {@code #} comments and blank lines ignored. A missing resource yields
   * an empty set, which for a mode list means every signature reads as new: loud, never silent.
   */
  private static Set<String> loadSignatureList(String resource) {
    Set<String> waived = new HashSet<>();
    try (InputStream in = FailClosedGateExtension.class.getResourceAsStream(resource)) {
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
      throw new IllegalStateException("cannot read " + resource, e);
    }
    return waived;
  }
}
