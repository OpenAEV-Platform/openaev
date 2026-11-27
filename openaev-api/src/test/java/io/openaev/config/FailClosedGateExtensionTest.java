package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.openaev.config.FailClosedAccessRecorder.Violation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Unit test of the WS1 gate's decision logic (no Spring): a fail-closed read from a NEW production
 * call site must fail the gate, while baselined production sites and all test/fixture callers are
 * waived. This is the proof that the gate can actually fail - a gate that never fails is worthless.
 */
class FailClosedGateExtensionTest {

  private static Violation from(String caller) {
    return new Violation(
        "collectors", caller, "main", "select ... where can_access_tenant(c.tenant_id)");
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
   * A baseline line may name the tables it waives ({@code <signature> tables=<t1>,<t2>}): the read
   * at that site is waived only when every active table it names is listed. The login and token
   * loads read {@code roles} with no scope on purpose; a read of another table at the same site is
   * a different question and stays offending.
   */
  @Nested
  @DisplayName("waivers limited to tables")
  class TableQualifiedWaivers {

    private static final String TOKEN_LOAD = "io.openaev.service.UserService.findByToken";

    private Violation read(String tables, String caller) {
      return new Violation(
          tables, caller, "main", "select ... where can_access_tenant(r.tenant_id, true)");
    }

    @Test
    @DisplayName("given_aReadOfTheListedTable_should_beWaived")
    void given_aReadOfTheListedTable_should_beWaived() {
      // Act
      List<String> offending =
          FailClosedGateExtension.offendingSignatures(List.of(read("roles", TOKEN_LOAD + ":810")));

      // Assert
      assertTrue(
          offending.isEmpty(), "a read of roles at the token load is waived, got " + offending);
    }

    @Test
    @DisplayName("given_aReadOfTheMarkingsOfTheGroupsAtTheUserLoads_should_beWaived")
    void given_aReadOfTheMarkingsOfTheGroupsAtTheUserLoads_should_beWaived() {
      // Arrange: the sites that load a user with no scope read the markings of its groups too
      List<String> sites =
          List.of(
              "io.openaev.rest.user.UserApi.login:81",
              TOKEN_LOAD + ":810",
              "io.openaev.service.UserService.resetPassword:751",
              "io.openaev.service.UserService.sendPasswordResetEmail:505",
              "io.openaev.service.UserService.user:267");

      // Act
      List<String> offending =
          FailClosedGateExtension.offendingSignatures(
              sites.stream().map(site -> read("marking_definitions", site)).toList());

      // Assert
      assertTrue(offending.isEmpty(), "the user loads are waived for markings, got " + offending);
    }

    @Test
    @DisplayName("given_aReadOfAnotherTableAtTheSameSite_should_beOffending")
    void given_aReadOfAnotherTableAtTheSameSite_should_beOffending() {
      // Act
      List<String> offending =
          FailClosedGateExtension.offendingSignatures(List.of(read("tags", TOKEN_LOAD + ":810")));

      // Assert
      assertEquals(List.of(TOKEN_LOAD), offending);
    }

    @Test
    @DisplayName("given_aReadOfTheListedTableAndAnother_should_beOffending")
    void given_aReadOfTheListedTableAndAnother_should_beOffending() {
      // Act
      List<String> offending =
          FailClosedGateExtension.offendingSignatures(
              List.of(read("roles,tags", TOKEN_LOAD + ":810")));

      // Assert
      assertEquals(List.of(TOKEN_LOAD), offending);
    }

    @Test
    @DisplayName("given_aReadWithNoTableLabel_should_beOffending")
    void given_aReadWithNoTableLabel_should_beOffending() {
      // Act
      List<String> offending =
          FailClosedGateExtension.offendingSignatures(
              List.of(read("(gated)", TOKEN_LOAD + ":810")));

      // Assert
      assertEquals(List.of(TOKEN_LOAD), offending);
    }

    @Test
    @DisplayName("given_aLineWithNoTables_should_waiveEveryTableAtItsSite")
    void given_aLineWithNoTables_should_waiveEveryTableAtItsSite() {
      // Act
      List<String> offending =
          FailClosedGateExtension.offendingSignatures(
              List.of(
                  read(
                      "marking_definitions",
                      "io.openaev.rest.collector.service.CollectorService.securityPlatformCollectors:206")));

      // Assert
      assertTrue(offending.isEmpty(), "a line with no tables waives its site, got " + offending);
    }
  }

  @Nested
  @DisplayName("reads of the groups table at the sites a caller of an empty scope reaches")
  class GroupsReads {

    private Violation read(String tables, String site) {
      return new Violation(
          tables, site + ":1", "main", "select ... where can_access_tenant(g.tenant_id)");
    }

    @Test
    @DisplayName("given_theGroupReadsOfTheEmptyScopeCallers_should_beWaived")
    void given_theGroupReadsOfTheEmptyScopeCallers_should_beWaived() {
      // Arrange: each read as the detector labels it in a production-list run
      List<Violation> reads =
          List.of(
              read("groups", "io.openaev.service.UserService.findWithRoles"),
              read("groups", "io.openaev.service.UserService.replaceTags"),
              read("groups", "io.openaev.service.UserService.resetPassword"),
              read("groups", "io.openaev.service.UserService.sendPasswordResetEmail"),
              read(
                  "groups",
                  "io.openaev.service.platform.groups.PlatformGroupService.lambda$search$1"),
              read(
                  "groups,marking_definitions",
                  "io.openaev.service.platform.groups.PlatformGroupService.lambda$search$1"),
              read(
                  "groups,scenarios",
                  "io.openaev.service.scenario.ScenarioService.lambda$scenarios$4"),
              read(
                  "assets,exercises,groups,injects",
                  "io.openaev.rest.exercise.service.ExerciseService.lambda$exercises$5"));

      // Act
      List<String> offending = FailClosedGateExtension.offendingSignatures(reads);

      // Assert
      assertTrue(offending.isEmpty(), "the baseline waives these group reads, got " + offending);
    }

    @Test
    @DisplayName("given_aGroupReadAtAnotherSite_should_beOffending")
    void given_aGroupReadAtAnotherSite_should_beOffending() {
      // Arrange: a read of groups where no waiver was written
      String site = "io.openaev.service.GroupReadNobodyWaived.load";

      // Act
      List<String> offending =
          FailClosedGateExtension.offendingSignatures(List.of(read("groups", site)));

      // Assert
      assertEquals(List.of(site), offending);
    }

    @Test
    @DisplayName("given_aReadOfGroupsAndAnotherTableAtAWaivedSite_should_beOffending")
    void given_aReadOfGroupsAndAnotherTableAtAWaivedSite_should_beOffending() {
      // Arrange
      String site = "io.openaev.service.UserService.resetPassword";

      // Act
      List<String> offending =
          FailClosedGateExtension.offendingSignatures(List.of(read("groups,tags", site)));

      // Assert
      assertEquals(List.of(site), offending);
    }
  }

  /**
   * The gate asserts per test in the normal pipeline and reports instead under a shadow mode. Three
   * directions, because a gate measured in two of them is not measured: the normal path still
   * fails, the shadow path does not fail, and a signature new to the armed mode's own list still
   * reaches the shadow report.
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
      // Arrange + Act: the live value, not a crafted one. It is empty in the pipeline and set by a
      // run armed by hand with a shadow mode, so asserting emptiness here would fail that run.
      // What must hold in both is that the decision follows the mode actually armed.
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
    @DisplayName("the shadow report marks a new signature so a reader can count it")
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
          "a reader counts lines prefixed '"
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

  /**
   * The user load of the access check reads the roles and the markings of the groups of a caller of
   * an empty scope. Each read is waived for its own table, at that site only.
   */
  @Nested
  @DisplayName("reads of the user load of the access check")
  class UserLoadOfTheAccessCheck {

    private static final String USER_LOAD = "io.openaev.service.UserService.findWithRoles";

    private Violation read(String tables, String site) {
      return new Violation(
          tables, site + ":1065", "main", "select ... where can_access_tenant(r.tenant_id)");
    }

    @Test
    @DisplayName("given_theRolesAndTheMarkingsOfTheGroupsAtTheUserLoad_should_beWaived")
    void given_theRolesAndTheMarkingsOfTheGroupsAtTheUserLoad_should_beWaived() {
      // Arrange: each read as the detector labels it in a production-list run
      List<Violation> reads =
          List.of(read("roles", USER_LOAD), read("marking_definitions", USER_LOAD));

      // Act
      List<String> offending = FailClosedGateExtension.offendingSignatures(reads);

      // Assert
      assertTrue(offending.isEmpty(), "the user load is waived for these tables, got " + offending);
    }

    @Test
    @DisplayName("given_aReadOfAnotherTableAtTheUserLoad_should_beOffending")
    void given_aReadOfAnotherTableAtTheUserLoad_should_beOffending() {
      // Act
      List<String> offending =
          FailClosedGateExtension.offendingSignatures(List.of(read("tags", USER_LOAD)));

      // Assert
      assertEquals(List.of(USER_LOAD), offending);
    }

    @Test
    @DisplayName("given_aReadOfAWaivedTableAndAnotherAtTheUserLoad_should_beOffending")
    void given_aReadOfAWaivedTableAndAnotherAtTheUserLoad_should_beOffending() {
      // Act: a read that names one more table than the waivers do is a different read
      List<String> offending =
          FailClosedGateExtension.offendingSignatures(List.of(read("roles,tags", USER_LOAD)));

      // Assert
      assertEquals(List.of(USER_LOAD), offending);
    }

    @Test
    @DisplayName("given_aReadOfRolesAtAnotherSite_should_beOffending")
    void given_aReadOfRolesAtAnotherSite_should_beOffending() {
      // Arrange
      String site = "io.openaev.service.UserService.nobodyWaivedThisLoad";

      // Act
      List<String> offending =
          FailClosedGateExtension.offendingSignatures(List.of(read("roles", site)));

      // Assert
      assertEquals(List.of(site), offending);
    }
  }

  @Nested
  @DisplayName("Tests that expect an empty scope")
  class ExpectedEmptyScope {

    @ExpectsEmptyScopeReads("a caller of no tenant reads nothing")
    class AnnotatedOuter {
      class NotAnnotatedInner {}
    }

    class NotAnnotatedOuter {
      @ExpectsEmptyScopeReads("a read with no scope")
      void annotatedMethod() {}

      void plainMethod() {}
    }

    private ExtensionContext contextOf(AnnotatedElement element, ExtensionContext parent) {
      ExtensionContext context = mock(ExtensionContext.class);
      when(context.getElement()).thenReturn(Optional.ofNullable(element));
      when(context.getParent()).thenReturn(Optional.ofNullable(parent));
      return context;
    }

    private Method method(Class<?> type, String name) throws NoSuchMethodException {
      return type.getDeclaredMethod(name);
    }

    @Test
    @DisplayName("given_anAnnotatedMethod_should_beExpected")
    void given_anAnnotatedMethod_should_beExpected() throws Exception {
      // Arrange
      ExtensionContext outer = contextOf(NotAnnotatedOuter.class, null);
      ExtensionContext test = contextOf(method(NotAnnotatedOuter.class, "annotatedMethod"), outer);

      // Act & Assert
      assertTrue(FailClosedGateExtension.expectsEmptyScopeReads(test));
    }

    @Test
    @DisplayName("given_aMethodOfAnAnnotatedClassNestedTwice_should_beExpected")
    void given_aMethodOfAnAnnotatedClassNestedTwice_should_beExpected() throws Exception {
      // Arrange
      ExtensionContext outer = contextOf(AnnotatedOuter.class, null);
      ExtensionContext inner = contextOf(AnnotatedOuter.NotAnnotatedInner.class, outer);
      ExtensionContext test = contextOf(method(NotAnnotatedOuter.class, "plainMethod"), inner);

      // Act & Assert
      assertTrue(FailClosedGateExtension.expectsEmptyScopeReads(test));
    }

    @Test
    @DisplayName("given_aMethodWithNoAnnotationAnywhere_should_notBeExpected")
    void given_aMethodWithNoAnnotationAnywhere_should_notBeExpected() throws Exception {
      // Arrange
      ExtensionContext outer = contextOf(NotAnnotatedOuter.class, null);
      ExtensionContext test = contextOf(method(NotAnnotatedOuter.class, "plainMethod"), outer);

      // Act & Assert
      assertFalse(FailClosedGateExtension.expectsEmptyScopeReads(test));
    }
  }

  @Nested
  @DisplayName("Reads a test waives for itself")
  class TestWaivedReads {

    private static final String SITE = "io.openaev.service.UserService.nobodyWaivedThisLoad";

    @WaivesUnscopedReads(
        value = "a member of no tenant reads no tag",
        reads = {SITE + " tables=tags"})
    class WaivingOuter {
      void plainMethod() {}
    }

    class PlainOuter {
      void plainMethod() {}

      @WaivesUnscopedReads(
          value = "an entry that would waive the whole site",
          reads = {SITE})
      void waivesAWholeSite() {}

      @WaivesUnscopedReads(
          value = "an entry that names the qualifier and no table",
          reads = {SITE + " tables= "})
      void waivesNoNamedTable() {}
    }

    private Violation read(String tables, String site) {
      return new Violation(
          tables, site + ":1", "main", "select ... where can_access_tenant(t.tenant_id)");
    }

    private ExtensionContext contextOf(AnnotatedElement element, ExtensionContext parent) {
      ExtensionContext context = mock(ExtensionContext.class);
      when(context.getElement()).thenReturn(Optional.ofNullable(element));
      when(context.getParent()).thenReturn(Optional.ofNullable(parent));
      return context;
    }

    @Test
    @DisplayName("given_aTestInsideAWaivingClass_should_waiveTheNamedReadOnly")
    void given_aTestInsideAWaivingClass_should_waiveTheNamedReadOnly() throws Exception {
      // Arrange
      ExtensionContext outer = contextOf(WaivingOuter.class, null);
      ExtensionContext test = contextOf(WaivingOuter.class.getDeclaredMethod("plainMethod"), outer);
      Set<String> waived = FailClosedGateExtension.waivedReadsOf(test);

      // Act
      List<String> offending =
          FailClosedGateExtension.offendingSignatures(
              List.of(read("tags", SITE), read("teams", SITE), read("tags", SITE + "Elsewhere")),
              waived);

      // Assert: the named table at the named site only
      assertEquals(List.of(SITE, SITE + "Elsewhere"), offending);
    }

    @Test
    @DisplayName("given_aTestWithoutTheAnnotation_should_waiveNothing")
    void given_aTestWithoutTheAnnotation_should_waiveNothing() throws Exception {
      // Arrange
      ExtensionContext outer = contextOf(PlainOuter.class, null);
      ExtensionContext test = contextOf(PlainOuter.class.getDeclaredMethod("plainMethod"), outer);

      // Act
      List<String> offending =
          FailClosedGateExtension.offendingSignatures(
              List.of(read("tags", SITE)), FailClosedGateExtension.waivedReadsOf(test));

      // Assert
      assertEquals(List.of(SITE), offending);
    }

    @Test
    @DisplayName("given_anEntryWithNoTable_should_beRefused")
    void given_anEntryWithNoTable_should_beRefused() throws Exception {
      // Arrange
      ExtensionContext outer = contextOf(PlainOuter.class, null);
      ExtensionContext wholeSite =
          contextOf(PlainOuter.class.getDeclaredMethod("waivesAWholeSite"), outer);
      ExtensionContext noTable =
          contextOf(PlainOuter.class.getDeclaredMethod("waivesNoNamedTable"), outer);

      // Act & Assert
      assertThrows(
          IllegalArgumentException.class, () -> FailClosedGateExtension.waivedReadsOf(wholeSite));
      assertThrows(
          IllegalArgumentException.class, () -> FailClosedGateExtension.waivedReadsOf(noTable));
    }
  }

  @Nested
  @DisplayName("the thread of each offending read")
  class ThreadOfTheRead {

    private static final String SITE = "io.openaev.service.ChannelService.validateArticles";
    private static final String SQL = "select ... where can_access_tenant(c.tenant_id)";

    @Test
    @DisplayName("given_offendingReadsOnTwoThreads_should_nameEachThreadUnderItsSite")
    void given_offendingReadsOnTwoThreads_should_nameEachThreadUnderItsSite() {
      // Arrange: the same site read from a scheduler thread and twice from the test thread
      List<Violation> reads =
          List.of(
              new Violation("channels", SITE + ":1", "scheduler_Worker-1", SQL),
              new Violation("channels", SITE + ":2", "main", SQL),
              new Violation("channels", SITE + ":1", "main", SQL));

      // Act
      String described = FailClosedGateExtension.describeOffendingReads(reads, Set.of());

      // Assert
      assertEquals(SITE + "\n    on thread main, scheduler_Worker-1", described);
    }
  }
}
