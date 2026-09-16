package io.openaev.config;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
 */
public class FailClosedGateExtension implements BeforeEachCallback, AfterEachCallback {

  private static final boolean ENABLED =
      "on".equals(System.getProperty("openaev.failclosed.detector"));
  private static final String UNKNOWN_CALLER = FailClosedDetectorListener.UNKNOWN_CALLER;
  private static final String BASELINE_RESOURCE = "/failclosed-baseline.txt";
  private static final Set<String> WAIVED = loadBaseline();

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
    if (!offending.isEmpty()) {
      fail(
          "Fail-closed: production code read an active tenant table with no scope (would return zero "
              + "rows in production):\n  "
              + String.join("\n  ", offending)
              + "\nScope the path (an HTTP TxCtx parameter, or the TenantScopedTransaction primitive "
              + "in background code). If it is intentional and safe, add the signature to "
              + "failclosed-baseline.txt with a reason.");
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
   * bug.
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
        || classFqn.contains(".composers.")) {
      return true;
    }
    String outer = classFqn.contains("$") ? classFqn.substring(0, classFqn.indexOf('$')) : classFqn;
    String simpleName = outer.substring(outer.lastIndexOf('.') + 1);
    return simpleName.endsWith("Test")
        || simpleName.endsWith("IT")
        || simpleName.endsWith("Benchmark");
  }

  private static Set<String> loadBaseline() {
    Set<String> waived = new HashSet<>();
    try (InputStream in = FailClosedGateExtension.class.getResourceAsStream(BASELINE_RESOURCE)) {
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
