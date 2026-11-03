package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.config.FailClosedAccessRecorder.Violation;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit test of the WS1 gate's decision logic (no Spring): a fail-closed read from a NEW production
 * call site must fail the gate, while baselined production sites and all test/fixture callers are
 * waived. This is the proof that the gate can actually fail - a gate that never fails is worthless.
 */
class FailClosedGateExtensionTest {

  private static Violation from(String caller) {
    return new Violation("collectors", caller, "select ... where can_access_tenant(c.tenant_id)");
  }

  @Test
  @DisplayName("a new production call site fails the gate")
  void newProductionSiteIsOffending() {
    List<String> offending =
        FailClosedGateExtension.offendingSignatures(
            List.of(from("io.openaev.service.NewlyBrokenService.readActiveTable:42")));
    assertEquals(List.of("io.openaev.service.NewlyBrokenService.readActiveTable"), offending);
  }

  @Test
  @DisplayName("a baselined production call site is waived")
  void baselinedSiteIsWaived() {
    List<String> offending =
        FailClosedGateExtension.offendingSignatures(
            List.of(
                from(
                    "io.openaev.rest.collector.service.CollectorService.securityPlatformCollectors:206")));
    assertTrue(offending.isEmpty(), "baselined production site must be waived, got " + offending);
  }

  @Test
  @DisplayName("test and fixture call sites are auto-waived")
  void testAndFixtureCallersAutoWaived() {
    List<String> offending =
        FailClosedGateExtension.offendingSignatures(
            List.of(
                from("io.openaev.config.SomethingTest.reads:10"),
                from("io.openaev.config.SomethingTest$Nested.reads:11"),
                from("io.openaev.utils.fixtures.composers.CollectorComposer$Composer.persist:50"),
                from("io.openaev.utilstest.DatabaseSnapshotManager.restore:83")));
    assertTrue(offending.isEmpty(), "test/fixture callers must be auto-waived, got " + offending);
  }

  /**
   * Same gap already measured on the write-attribution detector: {@code TestUserHolder.get}
   * (package {@code io.openaev.utils.mockUser}, class name does not end {@code Test}/{@code
   * IT}/{@code Benchmark}) was reported as a production caller in every tenant-creating test under
   * the shadow arguments. Both gates share this rule; folded here too.
   */
  @Test
  @DisplayName("the mock-user fixture plumbing is auto-waived")
  void mockUserFixturePlumbingAutoWaived() {
    List<String> offending =
        FailClosedGateExtension.offendingSignatures(
            List.of(
                from("io.openaev.utils.mockUser.TestUserHolder.get:33"),
                from(
                    "io.openaev.utils.mockUser.WithMockUserTestExecutionListener.beforeTestMethod:108"),
                from("io.openaev.utils.TenantIsolationTestHelper.createTenantWithCurrentUser:66")));
    assertTrue(
        offending.isEmpty(), "mock-user fixture plumbing must be auto-waived, got " + offending);
  }

  /**
   * The rule must not swallow the production utility classes that share the same {@code
   * io.openaev.utils} package name (compiled from {@code src/main}, not {@code src/test}): only the
   * {@code .mockUser.} sub-package and the {@code *TestHelper} suffix are test-only.
   */
  @Test
  @DisplayName("a production utils call site is still offending")
  void productionUtilsCallSiteStillOffending() {
    List<String> offending =
        FailClosedGateExtension.offendingSignatures(
            List.of(from("io.openaev.utils.AgentUtils.resolve:42")));
    assertEquals(List.of("io.openaev.utils.AgentUtils.resolve"), offending);
  }

  @Test
  @DisplayName("an unlocatable (unknown) caller does not fail the gate")
  void unknownCallerIsWaived() {
    assertTrue(FailClosedGateExtension.offendingSignatures(List.of(from("unknown"))).isEmpty());
  }

  @Test
  @DisplayName("the same new site is reported once")
  void duplicatesCollapsed() {
    List<String> offending =
        FailClosedGateExtension.offendingSignatures(
            List.of(
                from("io.openaev.service.NewlyBrokenService.readActiveTable:42"),
                from("io.openaev.service.NewlyBrokenService.readActiveTable:99")));
    assertEquals(1, offending.size());
  }

  /**
   * The gate asserts per test in the normal pipeline and reports instead under a shadow mode. Three
   * directions, because a gate measured in two of them is not measured: the normal path still
   * fails, the shadow path does not fail, and a signature new to the armed mode's own list still
   * reaches the shadow verdict.
   */
  @Nested
  @DisplayName("per-mode shadow reporting")
  class ShadowMode {

    private static final String NEW_SITE = "io.openaev.service.NewlyBrokenService.readActiveTable";

    @Test
    @DisplayName("with no shadow mode armed the gate still fails the test")
    void given_noShadowModeArmed_should_failThePerTestAssertion() {
      // Arrange + Act + Assert: the decision the afterEach callback takes on a non-empty
      // offending set. Empty mode means the normal pipeline, which must keep failing.
      assertTrue(
          FailClosedGateExtension.failsPerTest(""),
          "the normal pipeline must keep failing a test on a new production signature");
      assertTrue(FailClosedGateExtension.failsPerTest(null));
    }

    @Test
    @DisplayName("the running JVM's own mode and the gate's decision agree, in either pipeline")
    void given_thisJvm_should_decideFromItsOwnArmedMode() {
      // Arrange + Act: the live value, not a crafted one. It is empty in the normal pipeline and a
      // shadow mode in the nightly, which runs this very class in shard 9, so asserting emptiness
      // here would make the instrument fail its own nightly. What must hold in both is that the
      // decision follows the mode actually armed.
      String armed = FailClosedGateExtension.armedShadowMode();

      // Assert
      assertEquals(
          armed.isEmpty(),
          FailClosedGateExtension.failsPerTest(armed),
          "the per-test assertion must fire exactly when no shadow mode is armed, and this JVM's"
              + " armed mode is '"
              + armed
              + "'");
      if (!armed.isEmpty()) {
        assertTrue(
            FailClosedGateExtension.SHADOW_MODES.contains(armed),
            "a shadow run must arm a mode the gate accepts, got '" + armed + "'");
      }
    }

    @Test
    @DisplayName("with a shadow mode armed the gate does not fail the test")
    void given_shadowModeArmed_should_notFailThePerTestAssertion() {
      // Arrange + Act + Assert
      assertFalse(FailClosedGateExtension.failsPerTest("shadow-prod"));
      assertFalse(FailClosedGateExtension.failsPerTest("shadow-all"));
    }

    @Test
    @DisplayName("a signature absent from the armed mode's list is new")
    void given_signatureAbsentFromTheModeList_should_beNew() {
      // Arrange
      Set<String> modeList = Set.of("io.openaev.service.KnownService.read");

      // Act
      List<String> fresh =
          FailClosedGateExtension.newSignatures(
              List.of(NEW_SITE, "io.openaev.service.KnownService.read"), modeList);

      // Assert
      assertEquals(List.of(NEW_SITE), fresh);
    }

    @Test
    @DisplayName("a signature in the armed mode's list is not new")
    void given_signatureInTheModeList_should_notBeNew() {
      // Arrange + Act
      List<String> fresh =
          FailClosedGateExtension.newSignatures(List.of(NEW_SITE), Set.of(NEW_SITE));

      // Assert
      assertTrue(
          fresh.isEmpty(), "a signature in the mode's own list must not be new, got " + fresh);
    }

    @Test
    @DisplayName("the shadow report marks a new signature so the verdict can read it")
    void given_aNewSignature_should_bePrefixedNewInTheReport() {
      // Arrange + Act
      String content =
          FailClosedGateExtension.shadowReportContent(
              "shadow-prod",
              List.of(NEW_SITE, "io.openaev.service.KnownService.read"),
              Set.of("io.openaev.service.KnownService.read"));

      // Assert
      assertTrue(
          content.contains(FailClosedGateExtension.NEW_PREFIX + NEW_SITE),
          "the verdict counts lines prefixed '"
              + FailClosedGateExtension.NEW_PREFIX
              + "', got:\n"
              + content);
      assertTrue(
          content.contains(
              FailClosedGateExtension.KNOWN_PREFIX + "io.openaev.service.KnownService.read"),
          "a listed signature stays visible as known, got:\n" + content);
      assertTrue(content.startsWith("# "), "the report opens with a header line, got:\n" + content);
      assertTrue(
          content.contains("shadow-prod"), "the header names the armed mode, got:\n" + content);
    }

    @Test
    @DisplayName("a clean shadow report has no new line at all")
    void given_everySignatureListed_should_produceNoNewLine() {
      // Arrange + Act
      String content =
          FailClosedGateExtension.shadowReportContent(
              "shadow-all", List.of(NEW_SITE), Set.of(NEW_SITE));

      // Assert
      assertFalse(
          content.contains("\n" + FailClosedGateExtension.NEW_PREFIX),
          "nothing may be new when the mode's list covers it, got:\n" + content);
    }

    @Test
    @DisplayName("an unknown shadow mode is refused rather than silently unarmed")
    void given_anUnknownMode_should_beRefused() {
      // Arrange + Act + Assert: a typo must not read as "no mode armed", which would turn the
      // shadow back into a per-test gate without anyone noticing.
      assertThrows(
          IllegalArgumentException.class, () -> FailClosedGateExtension.shadowMode("shadow-prd"));
      assertEquals("shadow-prod", FailClosedGateExtension.shadowMode(" shadow-prod "));
      assertEquals("", FailClosedGateExtension.shadowMode(null));
      assertEquals("", FailClosedGateExtension.shadowMode("  "));
    }
  }
}
