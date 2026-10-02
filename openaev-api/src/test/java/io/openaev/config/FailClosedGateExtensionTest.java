package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.config.FailClosedAccessRecorder.Violation;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
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
}
