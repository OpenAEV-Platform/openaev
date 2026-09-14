package io.openaev.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import io.openaev.architecture.background_fixtures.AbstractExecutorFieldParentFixture;
import io.openaev.architecture.background_fixtures.AbstractInlineHandoffParentFixture;
import io.openaev.architecture.background_fixtures.AsyncMethodFixture;
import io.openaev.architecture.background_fixtures.BeanFactoryBackgroundFixture;
import io.openaev.architecture.background_fixtures.ChainedCompletableFutureFixture;
import io.openaev.architecture.background_fixtures.CommandLineRunnerFixture;
import io.openaev.architecture.background_fixtures.EventListenerFixture;
import io.openaev.architecture.background_fixtures.InheritedExecutorFieldFixture;
import io.openaev.architecture.background_fixtures.InheritedInlineHandoffFixture;
import io.openaev.architecture.background_fixtures.InheritedPostConstructFixture;
import io.openaev.architecture.background_fixtures.InlineCompletableFutureFixture;
import io.openaev.architecture.background_fixtures.InlineExecutorFixture;
import io.openaev.architecture.background_fixtures.InlineTaskSchedulerFixture;
import io.openaev.architecture.background_fixtures.NewThreadFixture;
import io.openaev.architecture.background_fixtures.PlainBeanFixture;
import io.openaev.architecture.background_fixtures.QuartzJobFixture;
import io.openaev.architecture.background_fixtures.RepeatableScheduledFixture;
import io.openaev.architecture.background_fixtures.ScheduledOnlyFixture;
import io.openaev.architecture.background_fixtures.TenantThreadFixture;
import io.openaev.architecture.background_fixtures.ThreadSubclassFixture;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Proves the background-guard's family detection and its until-active waiver check fire on the
 * near-miss shapes that a coarser guard misses (G7): a bean whose ONLY marker is
 * {@code @Scheduled}, inline {@code CompletableFuture.runAsync} / {@code new Thread} hand-offs with
 * no executor field, a marker inherited from an abstract parent, and a waiver whose table becomes
 * active. The frozen guard in {@link BackgroundEntrypointTenantScopeArchTest} runs on production
 * classes with {@code DoNotIncludeTests}, so these fixtures never reach its enumeration; here they
 * are imported explicitly and fed to the same detection methods.
 */
class BackgroundEntrypointDetectionTest {

  private static JavaClass imported(Class<?> fixture) {
    return new ClassFileImporter().importClasses(fixture).get(fixture);
  }

  @Test
  @DisplayName("a bean whose only marker is @Scheduled is caught as a family")
  void scheduledOnlyBeanIsAFamily() {
    List<String> families =
        BackgroundEntrypointTenantScopeArchTest.familiesOf(imported(ScheduledOnlyFixture.class));
    assertTrue(
        families.contains("scheduled"),
        "@Scheduled must be its own family; a scheduled-only bean escapes otherwise. Got: "
            + families);
  }

  @Test
  @DisplayName("a bean whose two @Scheduled land under the @Schedules container is caught")
  void repeatableScheduledBeanIsAFamily() {
    // Two @Scheduled on one method are stored by the compiler under @Schedules, and the individual
    // @Scheduled is then not directly present, so a @Scheduled-only check misses this poller.
    List<String> families =
        BackgroundEntrypointTenantScopeArchTest.familiesOf(
            imported(RepeatableScheduledFixture.class));
    assertTrue(
        families.contains("scheduled"),
        "a method carrying the repeatable @Schedules container must be the scheduled family. Got: "
            + families);
  }

  @Test
  @DisplayName("inline CompletableFuture.runAsync is caught as a detached hand-off")
  void inlineCompletableFutureIsAHandoff() {
    List<String> families =
        BackgroundEntrypointTenantScopeArchTest.familiesOf(
            imported(InlineCompletableFutureFixture.class));
    assertTrue(
        families.contains("handoff"),
        "an inline CompletableFuture.runAsync with no executor field must be a hand-off. Got: "
            + families);
  }

  @Test
  @DisplayName("a CompletableFuture *Async continuation (thenApplyAsync) is caught as a hand-off")
  void chainedCompletableFutureAsyncIsAHandoff() {
    // thenApplyAsync is neither supplyAsync nor runAsync; matching only those two names would let a
    // continuation hand-off to the default ForkJoinPool escape. The detector matches any
    // CompletableFuture method whose name ends in Async.
    List<String> families =
        BackgroundEntrypointTenantScopeArchTest.familiesOf(
            imported(ChainedCompletableFutureFixture.class));
    assertTrue(
        families.contains("handoff"),
        "an inline CompletableFuture.thenApplyAsync with no executor field must be a hand-off. Got: "
            + families);
  }

  @Test
  @DisplayName("a @Bean factory returning a background type is classified on its declaring class")
  void beanFactoryReturningBackgroundTypeIsClassifiedByDeclaringClass() {
    // The configuration class carries no marker of its own; the lambda instances its @Bean methods
    // return are anonymous and excluded from the scan, so the declaring class is recognised only by
    // reading the factory return types. Each type maps to its family: CommandLineRunner /
    // ApplicationRunner -> seeding, ApplicationListener -> listener, org.quartz.Job -> quartz.
    List<String> families =
        BackgroundEntrypointTenantScopeArchTest.familiesOf(
            imported(BeanFactoryBackgroundFixture.class));
    assertTrue(
        families.contains("seeding"),
        "a @Bean CommandLineRunner/ApplicationRunner factory must make its class the seeding family."
            + " Got: "
            + families);
    assertTrue(
        families.contains("listener"),
        "a @Bean ApplicationListener factory must make its class the listener family. Got: "
            + families);
    assertTrue(
        families.contains("quartz"),
        "a @Bean org.quartz.Job factory must make its class the quartz family. Got: " + families);
  }

  @Test
  @DisplayName("an inline new Thread is caught as a detached hand-off")
  void inlineNewThreadIsAHandoff() {
    List<String> families =
        BackgroundEntrypointTenantScopeArchTest.familiesOf(imported(NewThreadFixture.class));
    assertTrue(
        families.contains("handoff"),
        "an inline new Thread with no executor field must be a hand-off. Got: " + families);
  }

  @Test
  @DisplayName("an executor obtained and used inline is caught as a detached hand-off")
  void inlineExecutorSubmitIsAHandoff() {
    List<String> families =
        BackgroundEntrypointTenantScopeArchTest.familiesOf(imported(InlineExecutorFixture.class));
    assertTrue(
        families.contains("handoff"),
        "an executor obtained and used inline (Executors.new*().execute(...)) with no executor field"
            + " must be a hand-off. Got: "
            + families);
  }

  @Test
  @DisplayName("a subclass of Thread started inline is caught as a detached hand-off")
  void threadSubclassIsAHandoff() {
    // The constructor owner is the subclass, not java.lang.Thread, so an exact-owner check misses
    // it. Both classes are imported so the subclass relationship resolves to Thread.
    JavaClasses classes =
        new ClassFileImporter()
            .importClasses(ThreadSubclassFixture.class, TenantThreadFixture.class);
    List<String> families =
        BackgroundEntrypointTenantScopeArchTest.familiesOf(
            classes.get(ThreadSubclassFixture.class));
    assertTrue(
        families.contains("handoff"),
        "a new <ThreadSubclass>(...) with no executor field must be a hand-off. Got: " + families);
  }

  @Test
  @DisplayName("a TaskScheduler obtained and used inline is caught as a detached hand-off")
  void inlineTaskSchedulerIsAHandoff() {
    // TaskScheduler is not an Executor subtype, so a schedule(...) on a locally-obtained scheduler
    // is missed by an Executor-only receiver check and has no field for the field detector to see.
    List<String> families =
        BackgroundEntrypointTenantScopeArchTest.familiesOf(
            imported(InlineTaskSchedulerFixture.class));
    assertTrue(
        families.contains("handoff"),
        "an inline TaskScheduler.schedule(...) with no scheduler field must be a hand-off. Got: "
            + families);
  }

  @Test
  @DisplayName("a marker inherited from an abstract parent is caught (getAllMethods)")
  void inheritedMarkerIsAFamily() {
    // The concrete subclass redeclares nothing: its only marker is the parent's @PostConstruct.
    List<String> families =
        BackgroundEntrypointTenantScopeArchTest.familiesOf(
            imported(InheritedPostConstructFixture.class));
    assertTrue(
        families.contains("seeding"),
        "an inherited @PostConstruct must be seen on the concrete subclass. Got: " + families);
  }

  @Test
  @DisplayName("an executor field inherited from an abstract parent is caught (getAllFields)")
  void inheritedExecutorFieldIsAHandoff() {
    // The concrete subclass declares no field of its own: its only executor field is the parent's.
    // Both are imported so the inherited field resolves; getFields() would miss it, getAllFields()
    // sees it.
    JavaClasses classes =
        new ClassFileImporter()
            .importClasses(
                InheritedExecutorFieldFixture.class, AbstractExecutorFieldParentFixture.class);
    List<String> families =
        BackgroundEntrypointTenantScopeArchTest.familiesOf(
            classes.get(InheritedExecutorFieldFixture.class));
    assertTrue(
        families.contains("handoff"),
        "an executor field owned by an abstract parent must be seen on the concrete subclass. Got: "
            + families);
  }

  @Test
  @DisplayName("an inline hand-off inherited from an abstract parent is caught (getAllMethods)")
  void inheritedInlineHandoffIsAHandoff() {
    // The concrete subclass declares nothing: its only hand-off (CompletableFuture.supplyAsync) is
    // in the body of a method inherited from the abstract parent. A scan of the child's own method
    // calls misses it; the scan must read the inherited method body. Both classes are imported so
    // the inherited body resolves, as with the inherited-field fixture.
    JavaClasses classes =
        new ClassFileImporter()
            .importClasses(
                InheritedInlineHandoffFixture.class, AbstractInlineHandoffParentFixture.class);
    List<String> families =
        BackgroundEntrypointTenantScopeArchTest.familiesOf(
            classes.get(InheritedInlineHandoffFixture.class));
    assertTrue(
        families.contains("handoff"),
        "an inline hand-off owned by an abstract parent's method must be seen on the concrete"
            + " subclass. Got: "
            + families);
  }

  @Test
  @DisplayName("a Quartz Job is caught as a family")
  void quartzJobIsAFamily() {
    List<String> families =
        BackgroundEntrypointTenantScopeArchTest.familiesOf(imported(QuartzJobFixture.class));
    assertTrue(
        families.contains("quartz"),
        "a class implementing org.quartz.Job must be the quartz family. Got: " + families);
  }

  @Test
  @DisplayName("an @Async method is caught as a family")
  void asyncMethodIsAFamily() {
    List<String> families =
        BackgroundEntrypointTenantScopeArchTest.familiesOf(imported(AsyncMethodFixture.class));
    assertTrue(
        families.contains("async"),
        "a bean with an @Async method must be the async family. Got: " + families);
  }

  @Test
  @DisplayName("an @EventListener method is caught as a family")
  void eventListenerIsAFamily() {
    List<String> families =
        BackgroundEntrypointTenantScopeArchTest.familiesOf(imported(EventListenerFixture.class));
    assertTrue(
        families.contains("listener"),
        "a bean with an @EventListener method must be the listener family. Got: " + families);
  }

  @Test
  @DisplayName("a CommandLineRunner is caught as a family")
  void commandLineRunnerIsAFamily() {
    List<String> families =
        BackgroundEntrypointTenantScopeArchTest.familiesOf(
            imported(CommandLineRunnerFixture.class));
    assertTrue(
        families.contains("seeding"),
        "a class implementing CommandLineRunner must be the seeding family. Got: " + families);
  }

  @Test
  @DisplayName("a plain bean belongs to no family (detection is not trivially always-true)")
  void plainBeanIsNoFamily() {
    assertEquals(
        List.of(),
        BackgroundEntrypointTenantScopeArchTest.familiesOf(imported(PlainBeanFixture.class)),
        "a class with no marker must belong to no family");
  }

  @Test
  @DisplayName(
      "the scan reports an unlisted concrete entry point, and a baseline entry silences it")
  void unlistedConcreteEntrypointIsReported() {
    // The production guard only ever runs the scan over the frozen production import, so nothing
    // proves the scan reports an unclassified class or that the assertion still fires. Drive an
    // unlisted concrete fixture (not on the primitive) through the same seam.
    JavaClasses classes = new ClassFileImporter().importClasses(QuartzJobFixture.class);
    String fqcn = QuartzJobFixture.class.getName();

    Map<String, List<String>> unclassified =
        BackgroundEntrypointTenantScopeArchTest.unclassifiedEntrypoints(classes, Map.of());
    assertTrue(
        unclassified.containsKey(fqcn) && unclassified.get(fqcn).contains("quartz"),
        "an unlisted concrete background entry point must be reported by the scan. Got: "
            + unclassified);

    // Near miss: the same class classified in the baseline must NOT be reported, so the check keys
    // on the baseline and is not trivially always-reporting.
    Map<String, List<String>> classified =
        BackgroundEntrypointTenantScopeArchTest.unclassifiedEntrypoints(
            classes, Map.of(fqcn, "platform-global"));
    assertTrue(
        classified.isEmpty(),
        "a concrete entry point listed in the baseline must not be reported. Got: " + classified);
  }

  @Test
  @DisplayName("a waiver expires when its named table appears in the active-tables list")
  void waiverExpiresWhenTableActivates() {
    Map<String, String> baseline =
        Map.of("io.openaev.Fixture", "cross-tenant-resolve until-active:injects");
    assertTrue(
        BackgroundEntrypointTenantScopeArchTest.expiredWaivers(
                baseline, Set.of("tags", "injects"), Set.of())
            .stream()
            .anyMatch(s -> s.contains("io.openaev.Fixture")),
        "the waiver must expire once 'injects' is active");
    assertTrue(
        BackgroundEntrypointTenantScopeArchTest.expiredWaivers(baseline, Set.of("tags"), Set.of())
            .isEmpty(),
        "the same waiver must NOT expire while 'injects' is still v1 (red for the right reason)");
  }

  @Test
  @DisplayName("a waiver expires under '*' only when its table is one '*' actually activates")
  void waiverExpiresUnderWildcard() {
    Map<String, String> baseline =
        Map.of("io.openaev.Fixture", "cross-tenant-resolve until-active:reporting_schedules");
    assertTrue(
        BackgroundEntrypointTenantScopeArchTest.expiredWaivers(
                baseline, Set.of("*"), Set.of("reporting_schedules", "injects"))
            .stream()
            .anyMatch(s -> s.contains("io.openaev.Fixture")),
        "'*' activates reporting_schedules (a strict table), so the waiver must expire");
  }

  @Test
  @DisplayName("a waiver on a table '*' does not activate (dual-scope) does NOT expire under '*'")
  void dualScopeWaiverDoesNotExpireUnderWildcard() {
    // 'groups' is a dual-scope table; '*' activates strict tables only, so a waiver named against a
    // dual-scope table must not be treated as expired the moment '*' appears.
    Map<String, String> baseline =
        Map.of("io.openaev.Fixture", "cross-tenant-resolve until-active:groups");
    assertTrue(
        BackgroundEntrypointTenantScopeArchTest.expiredWaivers(
                baseline, Set.of("*"), Set.of("reporting_schedules", "injects"))
            .isEmpty(),
        "'*' does not activate dual-scope tables, so a waiver on 'groups' must not spuriously"
            + " expire");
  }

  @Test
  @DisplayName("a dual-scope until-active waiver expires when its table is listed explicitly")
  void dualScopeWaiverExpiresWhenListedExplicitly() {
    // '*' never activates 'groups' (a dual table), but an explicit allowlist can, so a
    // cross-tenant-resolve until-active:groups waiver must expire the day 'groups' is listed.
    Map<String, String> baseline =
        Map.of("io.openaev.Fixture", "cross-tenant-resolve until-active:groups");
    assertTrue(
        BackgroundEntrypointTenantScopeArchTest.expiredWaivers(
                baseline, Set.of("groups"), Set.of("reporting_schedules"))
            .stream()
            .anyMatch(s -> s.contains("io.openaev.Fixture")),
        "an explicit allowlist naming 'groups' must expire the dual-scope waiver");
    assertTrue(
        BackgroundEntrypointTenantScopeArchTest.expiredWaivers(
                baseline, Set.of("tags"), Set.of("reporting_schedules"))
            .isEmpty(),
        "while 'groups' is still v1 the waiver must not expire (red for the right reason)");
  }

  @Test
  @DisplayName(
      "an until-active tag naming an unknown table is rejected through the validation seam")
  void unknownUntilActiveTableIsRejected() {
    Set<String> known = BackgroundEntrypointTenantScopeArchTest.knownTenantTables();
    Map<String, String> baseline =
        Map.of(
            "io.openaev.Ok", "cross-tenant-resolve until-active:injects",
            "io.openaev.Typo", "cross-tenant-resolve until-active:injcts_typo");
    List<String> unknown =
        BackgroundEntrypointTenantScopeArchTest.unknownUntilActiveTables(baseline, known);
    assertTrue(
        unknown.stream().anyMatch(s -> s.contains("io.openaev.Typo") && s.contains("injcts_typo")),
        "a typo'd table name must be rejected by the validation, not merely absent from the known"
            + " set. Got: "
            + unknown);
    assertTrue(
        unknown.stream().noneMatch(s -> s.contains("io.openaev.Ok")),
        "a real strict table ('injects') must not be flagged. Got: " + unknown);
  }

  @Test
  @DisplayName("a dual-scope table is a legal until-active target; outside-v2 and typos are not")
  void untilActiveTargetsAreEveryActivatableTable() {
    Set<String> known = BackgroundEntrypointTenantScopeArchTest.knownTenantTables();
    assertTrue(known.contains("injects"), "a strict table '*' activates must be known");
    assertTrue(known.contains("reporting_schedules"), "a strict table '*' activates must be known");
    // A dual-scope table is never activated by '*', but TenantTables.restrictTo accepts it in an
    // explicit allowlist, so an until-active:<dual> waiver has an activation to expire it and must
    // be a legal target (otherwise it is rejected as unknown and never expires).
    assertTrue(
        known.contains("groups"),
        "a dual-scope table an explicit allowlist can activate must be a legal until-active target");
    assertTrue(
        !known.contains("attackpath_graph_version"),
        "a strict table permanently outside v2 is never activated, so it must not be a legal"
            + " until-active target");
    assertTrue(
        !known.contains("injcts_typo"),
        "a typo'd table name must not be a legal until-active" + " target");

    // Not just membership of the known set: drive the tags through the actual validation seam. A
    // regression that dropped dual tables from the legal set (rejecting a legal waiver) or that
    // stopped rejecting outside-v2 targets fails here. 'groups' is dual-scope and now legal;
    // 'attackpath_graph_version' is strict but permanently outside v2 (never activated); 'injects'
    // is a real strict target.
    Map<String, String> baseline =
        Map.of(
            "io.openaev.Dual", "cross-tenant-resolve until-active:groups",
            "io.openaev.Outside", "cross-tenant-resolve until-active:attackpath_graph_version",
            "io.openaev.Ok", "cross-tenant-resolve until-active:injects");
    List<String> unknown =
        BackgroundEntrypointTenantScopeArchTest.unknownUntilActiveTables(baseline, known);
    assertTrue(
        unknown.stream().noneMatch(s -> s.contains("io.openaev.Dual")),
        "a dual-scope table tag must be accepted (an explicit allowlist can activate it). Got: "
            + unknown);
    assertTrue(
        unknown.stream()
            .anyMatch(
                s -> s.contains("io.openaev.Outside") && s.contains("attackpath_graph_version")),
        "an outside-v2 table tag must be rejected by the validation, not merely absent. Got: "
            + unknown);
    assertTrue(
        unknown.stream().noneMatch(s -> s.contains("io.openaev.Ok")),
        "a real strict target ('injects') must not be flagged. Got: " + unknown);
  }

  @Test
  @DisplayName("a duplicate FQCN in the baseline is rejected, naming both line numbers")
  void duplicateFqcnIsRejected() {
    List<BackgroundEntrypointTenantScopeArchTest.RawLine> entries =
        List.of(
            new BackgroundEntrypointTenantScopeArchTest.RawLine(
                3, "io.openaev.Fixture", "cross-tenant-resolve until-active:injects"),
            new BackgroundEntrypointTenantScopeArchTest.RawLine(
                7, "io.openaev.Fixture", "touches-no-tenant-table: nothing"));
    IllegalStateException thrown =
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalStateException.class,
            () -> BackgroundEntrypointTenantScopeArchTest.baselineFrom(entries),
            "a duplicate FQCN must be rejected, not silently collapsed");
    assertTrue(
        thrown.getMessage().contains("io.openaev.Fixture")
            && thrown.getMessage().contains("3")
            && thrown.getMessage().contains("7"),
        "the rejection must name the class and both line numbers. Got: " + thrown.getMessage());
    List<BackgroundEntrypointTenantScopeArchTest.RawLine> distinct =
        List.of(
            new BackgroundEntrypointTenantScopeArchTest.RawLine(
                3, "io.openaev.A", "platform-global: x"),
            new BackgroundEntrypointTenantScopeArchTest.RawLine(
                7, "io.openaev.B", "platform-global: y"));
    assertEquals(
        2,
        BackgroundEntrypointTenantScopeArchTest.baselineFrom(distinct).size(),
        "distinct FQCNs must load without error");
  }

  @Test
  @DisplayName("an entry with a blank reason is rejected through the injected seam")
  void blankReasonEntryIsRejectedThroughTheSeam() {
    // every_baseline_entry_carries_a_reason only ever runs over the checked-in file, whose entries
    // all currently have reasons, so a regression that deleted the blank check would stay green.
    // Drive a blank-reason line through the extracted seam, like the duplicate and waiver checks.
    List<BackgroundEntrypointTenantScopeArchTest.RawLine> entries =
        List.of(
            new BackgroundEntrypointTenantScopeArchTest.RawLine(4, "io.openaev.NoReason", ""),
            new BackgroundEntrypointTenantScopeArchTest.RawLine(9, "io.openaev.Blank", "   "),
            new BackgroundEntrypointTenantScopeArchTest.RawLine(
                12, "io.openaev.Ok", "platform-global: fine"));
    List<String> malformed = BackgroundEntrypointTenantScopeArchTest.entriesWithoutReason(entries);
    assertTrue(
        malformed.stream().anyMatch(s -> s.contains("io.openaev.NoReason") && s.contains("4")),
        "an empty reason must be flagged, naming its line number. Got: " + malformed);
    assertTrue(
        malformed.stream().anyMatch(s -> s.contains("io.openaev.Blank") && s.contains("9")),
        "a whitespace-only reason must be flagged, naming its line number. Got: " + malformed);
    assertTrue(
        malformed.stream().noneMatch(s -> s.contains("io.openaev.Ok")),
        "an entry that carries a reason must not be flagged. Got: " + malformed);
  }

  @Test
  @DisplayName("a reason must begin with a documented classification, arbitrary text is rejected")
  void reasonGrammarRejectsArbitraryText() {
    assertTrue(
        BackgroundEntrypointTenantScopeArchTest.isWellFormedReason("touches-no-tenant-table: x"),
        "touches-no-tenant-table is a documented classification");
    assertTrue(
        BackgroundEntrypointTenantScopeArchTest.isWellFormedReason(
            "delegates-to-Foo#bar: delegates"),
        "delegates-to-<Class>#<method> is a documented classification");
    assertTrue(
        BackgroundEntrypointTenantScopeArchTest.isWellFormedReason(
            "cross-tenant-resolve until-active:injects: x"),
        "cross-tenant-resolve with an until-active tag is well formed");
    assertTrue(
        !BackgroundEntrypointTenantScopeArchTest.isWellFormedReason("temporary"),
        "arbitrary text must be rejected");
    assertTrue(
        !BackgroundEntrypointTenantScopeArchTest.isWellFormedReason("until-active:injects only"),
        "a bare until-active tag with no classification must be rejected");
  }

  @Test
  @DisplayName(
      "delegates-to must name a method and cross-tenant-resolve must carry an until-active")
  void grammarRequiresMethodSeparatorAndTag() {
    // delegates-to needs the <Class>#<method> separator: a bare delegate names nothing.
    assertTrue(
        !BackgroundEntrypointTenantScopeArchTest.isWellFormedReason("delegates-to-Foo"),
        "delegates-to without a #method separator must be rejected");
    assertTrue(
        BackgroundEntrypointTenantScopeArchTest.isWellFormedReason(
            "delegates-to-ReportingScheduleService#runDueSchedules until-active:reporting_schedules"),
        "delegates-to with #method (and an optional until-active tag) is well formed");

    // cross-tenant-resolve is honest only while a table stays v1, so it MUST carry the tag that
    // expires it; a bare form or one whose only tag is malformed launders a permanent waiver.
    assertTrue(
        !BackgroundEntrypointTenantScopeArchTest.isWellFormedReason("cross-tenant-resolve"),
        "a bare cross-tenant-resolve with no until-active tag must be rejected");
    assertTrue(
        !BackgroundEntrypointTenantScopeArchTest.isWellFormedReason(
            "cross-tenant-resolve: launders an unscoped write"),
        "cross-tenant-resolve with free text but no until-active tag must be rejected");
    assertTrue(
        !BackgroundEntrypointTenantScopeArchTest.isWellFormedReason(
            "cross-tenant-resolve until-active:injects-typo"),
        "a malformed (hyphenated) until-active tag is no tag, so cross-tenant-resolve is rejected");

    // touches-no-tenant-table and platform-global are permanent: an until-active tag is a
    // contradiction (nothing activates to make them wrong) and must be rejected, so a temporary
    // waiver cannot hide behind a permanent label.
    assertTrue(
        !BackgroundEntrypointTenantScopeArchTest.isWellFormedReason(
            "touches-no-tenant-table until-active:injects"),
        "an until-active tag on the permanent touches-no-tenant-table must be rejected");
    assertTrue(
        !BackgroundEntrypointTenantScopeArchTest.isWellFormedReason(
            "platform-global until-active:injects"),
        "an until-active tag on the permanent platform-global must be rejected");
  }

  @Test
  @DisplayName(
      "a malformed until-active token is rejected for every classification, not just"
          + " cross-tenant-resolve")
  void malformedUntilActiveIsRejectedForEveryClassification() {
    // The tag extractor returns nothing for a malformed until-active token (a hyphenated name, a
    // missing colon, an empty table). For the two PERMANENT classifications that is read as "no
    // tag", which they accept, and for delegates-to the tag is optional, so the malformed token
    // slips through and the unknown-table / expiry checks never see it: a typo launders a permanent
    // waiver. Every classification must reject a malformed until-active token before its own rule.
    assertTrue(
        !BackgroundEntrypointTenantScopeArchTest.isWellFormedReason(
            "platform-global until-active:injects-typo"),
        "a malformed (hyphenated) until-active tag on platform-global must be rejected, not read as"
            + " no tag");
    assertTrue(
        !BackgroundEntrypointTenantScopeArchTest.isWellFormedReason(
            "delegates-to-Foo#bar until-active:injects-typo"),
        "a malformed (hyphenated) until-active tag on delegates-to must be rejected, not treated as"
            + " an absent optional tag");

    // Near misses: an empty table after the colon, and a space breaking the colon, are both
    // malformed tokens and must be rejected wherever they appear.
    assertTrue(
        !BackgroundEntrypointTenantScopeArchTest.isWellFormedReason(
            "delegates-to-Foo#bar until-active:"),
        "an until-active token with no table must be rejected");
    assertTrue(
        !BackgroundEntrypointTenantScopeArchTest.isWellFormedReason(
            "delegates-to-Foo#bar until-active :injects"),
        "an until-active token whose colon is broken by a space must be rejected");

    // A well-formed tag on a permanent classification is already rejected (nothing activates to
    // make
    // it wrong); keep that, so this test is not merely rejecting every until-active mention.
    assertTrue(
        !BackgroundEntrypointTenantScopeArchTest.isWellFormedReason(
            "platform-global until-active:injects"),
        "a well-formed tag on the permanent platform-global stays rejected");

    // A well-formed tag where it is allowed (delegates-to, cross-tenant-resolve) must still pass:
    // the rejection is of malformed tokens, not of the legal until-active shape.
    assertTrue(
        BackgroundEntrypointTenantScopeArchTest.isWellFormedReason(
            "delegates-to-Foo#bar until-active:injects"),
        "a well-formed until-active tag on delegates-to must still be accepted");
    assertTrue(
        BackgroundEntrypointTenantScopeArchTest.isWellFormedReason(
            "cross-tenant-resolve until-active:reporting_schedules: free text after a colon"),
        "a well-formed tag followed by colon-separated free text must still be accepted");
  }

  @Test
  @DisplayName("a waived class made abstract is stale (the guard no longer enumerates it)")
  void abstractWaivedClassIsStale() {
    // The production scan skips non-concrete classes via isConcreteBean, so a waived class later
    // made abstract while it still carries a background marker is no longer enumerated by the
    // guard, yet familiesOf stays non-empty. The stale check must apply the same isConcreteBean
    // filter, or the waiver becomes a permanent, un-enumerated exemption. AbstractInlineHandoff
    // ParentFixture is abstract and is a hand-off family, so it stands in for that shape.
    JavaClass abstractEntry = imported(AbstractInlineHandoffParentFixture.class);
    assertTrue(
        BackgroundEntrypointTenantScopeArchTest.familiesOf(abstractEntry).contains("handoff"),
        "the fixture must still look like a background entry point (non-empty families)");
    Map<String, JavaClass> byName = Map.of(abstractEntry.getFullName(), abstractEntry);
    Map<String, String> baseline =
        Map.of(abstractEntry.getFullName(), "touches-no-tenant-table: nothing");
    List<String> stale = BackgroundEntrypointTenantScopeArchTest.staleEntries(baseline, byName);
    assertTrue(
        stale.stream().anyMatch(s -> s.contains(abstractEntry.getFullName())),
        "a waived class made abstract must be flagged stale so its waiver is removed. Got: "
            + stale);

    // Near miss: a concrete background entry point with the same waiver must NOT be flagged, so the
    // check keys on isConcreteBean, not on being a fixture.
    JavaClass concreteEntry = imported(QuartzJobFixture.class);
    assertTrue(
        BackgroundEntrypointTenantScopeArchTest.staleEntries(
                Map.of(concreteEntry.getFullName(), "touches-no-tenant-table: nothing"),
                Map.of(concreteEntry.getFullName(), concreteEntry))
            .isEmpty(),
        "a concrete, still-recognised background entry point must not be flagged stale");
  }

  @Test
  @DisplayName(
      "a background job that writes tenant-bearing tables is not waived touches-no-tenant-table")
  void writesToTenantTableAreNotWaivedAsTouchingNothing() {
    // OpenCTIConnectorRegisterPingJob's flow writes Group/Role (DualScopeBase) and the strict
    // users_tenants join (TenantUserService.attachToTenant); AiMetricCollector reads the
    // platform Setting row (DualScopeBase). Neither "touches no tenant table"; both are
    // platform-global. This pins the reclassification against a revert to the false reason.
    Map<String, String> baseline = BackgroundEntrypointTenantScopeArchTest.loadBaseline();
    assertClassification(
        baseline, "io.openaev.scheduler.jobs.OpenCTIConnectorRegisterPingJob", "platform-global");
    assertClassification(
        baseline, "io.openaev.telemetry.metric_collectors.AiMetricCollector", "platform-global");

    // Near miss: a class that genuinely touches nothing keeps touches-no-tenant-table, so this
    // test is not merely asserting platform-global everywhere.
    assertClassification(baseline, "io.openaev.debug.DebugModeManager", "touches-no-tenant-table");
  }

  private static void assertClassification(
      Map<String, String> baseline, String fqcn, String classification) {
    String reason = baseline.get(fqcn);
    assertTrue(reason != null, fqcn + " must be listed in the baseline");
    assertTrue(
        reason.startsWith(classification),
        fqcn + " must be classified '" + classification + "', got: " + reason);
  }
}
