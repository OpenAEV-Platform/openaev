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
import io.openaev.architecture.background_fixtures.ComposedAsync;
import io.openaev.architecture.background_fixtures.ComposedAsyncFixture;
import io.openaev.architecture.background_fixtures.ComposedBean;
import io.openaev.architecture.background_fixtures.ComposedBeanFactoryFixture;
import io.openaev.architecture.background_fixtures.ComposedEventListener;
import io.openaev.architecture.background_fixtures.ComposedEventListenerFixture;
import io.openaev.architecture.background_fixtures.ComposedScheduled;
import io.openaev.architecture.background_fixtures.ComposedScheduledFixture;
import io.openaev.architecture.background_fixtures.EventListenerFixture;
import io.openaev.architecture.background_fixtures.InheritedExecutorFieldFixture;
import io.openaev.architecture.background_fixtures.InheritedInlineHandoffFixture;
import io.openaev.architecture.background_fixtures.InheritedPostConstructFixture;
import io.openaev.architecture.background_fixtures.InlineCompletableFutureFixture;
import io.openaev.architecture.background_fixtures.InlineExecutorFixture;
import io.openaev.architecture.background_fixtures.InlineTaskSchedulerFixture;
import io.openaev.architecture.background_fixtures.NewThreadFixture;
import io.openaev.architecture.background_fixtures.PlainBeanFixture;
import io.openaev.architecture.background_fixtures.PrimitiveBackedHandoffFixture;
import io.openaev.architecture.background_fixtures.QuartzJobFixture;
import io.openaev.architecture.background_fixtures.RepeatableScheduledFixture;
import io.openaev.architecture.background_fixtures.ScheduledOnlyFixture;
import io.openaev.architecture.background_fixtures.TenantThreadFixture;
import io.openaev.architecture.background_fixtures.ThreadSubclassFixture;
import io.openaev.context.TenantScopedTransaction;
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
  @DisplayName("a method annotated through a composed @Async is caught as the async family")
  void composedAsyncMethodIsAFamily() {
    // Spring applies @Async through meta-annotations, so a method marked with a custom annotation
    // meta-annotated @Async runs on a pool thread just as a direct @Async would. A direct-only
    // check sees no @Async; the meta-annotation-aware predicate must catch it. The composed
    // annotation is imported alongside the fixture so its meta-annotations resolve.
    JavaClasses classes =
        new ClassFileImporter().importClasses(ComposedAsyncFixture.class, ComposedAsync.class);
    List<String> families =
        BackgroundEntrypointTenantScopeArchTest.familiesOf(classes.get(ComposedAsyncFixture.class));
    assertTrue(
        families.contains("async"),
        "a method meta-annotated @Async through a composed annotation must be the async family."
            + " Got: "
            + families);
  }

  @Test
  @DisplayName("a method annotated through a composed @Scheduled is caught as the scheduled family")
  void composedScheduledMethodIsAFamily() {
    JavaClasses classes =
        new ClassFileImporter()
            .importClasses(ComposedScheduledFixture.class, ComposedScheduled.class);
    List<String> families =
        BackgroundEntrypointTenantScopeArchTest.familiesOf(
            classes.get(ComposedScheduledFixture.class));
    assertTrue(
        families.contains("scheduled"),
        "a method meta-annotated @Scheduled through a composed annotation (@EveryFiveSeconds shape)"
            + " must be the scheduled family. Got: "
            + families);
  }

  @Test
  @DisplayName(
      "a method annotated through a composed @EventListener is caught as the listener" + " family")
  void composedEventListenerMethodIsAFamily() {
    JavaClasses classes =
        new ClassFileImporter()
            .importClasses(ComposedEventListenerFixture.class, ComposedEventListener.class);
    List<String> families =
        BackgroundEntrypointTenantScopeArchTest.familiesOf(
            classes.get(ComposedEventListenerFixture.class));
    assertTrue(
        families.contains("listener"),
        "a method meta-annotated @EventListener through a composed annotation must be the listener"
            + " family. Got: "
            + families);
  }

  @Test
  @DisplayName("a @Bean factory annotated through a composed @Bean is classified by its class")
  void composedBeanFactoryIsClassified() {
    // The declaring class carries no marker of its own; the factory method is annotated with a
    // composed @Bean, not a direct one, and returns a CommandLineRunner lambda (seeding). A
    // direct-only @Bean check on declaresBeanReturning misses it.
    JavaClasses classes =
        new ClassFileImporter().importClasses(ComposedBeanFactoryFixture.class, ComposedBean.class);
    List<String> families =
        BackgroundEntrypointTenantScopeArchTest.familiesOf(
            classes.get(ComposedBeanFactoryFixture.class));
    assertTrue(
        families.contains("seeding"),
        "a @Bean CommandLineRunner factory annotated through a composed @Bean must make its class"
            + " the seeding family. Got: "
            + families);
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
  @DisplayName(
      "a dual-scope table is a legal until-active target; self-isolated tables and typos are not")
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
        "a self-isolated strict table is never activated, so it must not be a legal"
            + " until-active target");
    assertTrue(
        !known.contains("injcts_typo"),
        "a typo'd table name must not be a legal until-active" + " target");

    // Not just membership of the known set: drive the tags through the actual validation seam. A
    // regression that dropped dual tables from the legal set (rejecting a legal waiver) or that
    // stopped rejecting self-isolated targets fails here. 'groups' is dual-scope and now legal;
    // 'attackpath_graph_version' is strict but self-isolated (never activated); 'injects'
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
        "a self-isolated table tag must be rejected by the validation, not merely absent. Got: "
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
  @DisplayName("a baseline entry whose class is gone, or now on the primitive, is stale")
  void staleWhenClassMissingOrOnPrimitive() {
    // Branch clazz == null: a baseline naming a class no longer in the production import is stale.
    // abstractWaivedClassIsStale only drives the !isConcreteBean arm; without this the clazz ==
    // null
    // branch could be removed and the suite stay green.
    List<String> missing =
        BackgroundEntrypointTenantScopeArchTest.staleEntries(
            Map.of("io.openaev.Gone", "platform-global: removed"), Map.of());
    assertTrue(
        missing.stream()
            .anyMatch(s -> s.contains("io.openaev.Gone") && s.contains("not a production class")),
        "a baseline entry naming a class that no longer exists must be flagged stale. Got: "
            + missing);

    // Branch isOnPrimitive: a concrete background class that now references TenantScopedTransaction
    // no longer needs a waiver. Import the primitive with the fixture so the direct dependency
    // resolves.
    JavaClasses onPrimitiveClasses =
        new ClassFileImporter()
            .importClasses(PrimitiveBackedHandoffFixture.class, TenantScopedTransaction.class);
    JavaClass onPrimitive = onPrimitiveClasses.get(PrimitiveBackedHandoffFixture.class);
    assertTrue(
        BackgroundEntrypointTenantScopeArchTest.familiesOf(onPrimitive).contains("handoff"),
        "the fixture must still look like a background entry point (non-empty families)");
    List<String> onPrimitiveStale =
        BackgroundEntrypointTenantScopeArchTest.staleEntries(
            Map.of(onPrimitive.getFullName(), "touches-no-tenant-table: nothing"),
            Map.of(onPrimitive.getFullName(), onPrimitive));
    assertTrue(
        onPrimitiveStale.stream()
            .anyMatch(
                s ->
                    s.contains(onPrimitive.getFullName())
                        && s.contains("references the primitive")),
        "a waived class that now references the primitive must be flagged stale. Got: "
            + onPrimitiveStale);

    // Near miss: a concrete background class OFF the primitive keeps its waiver, so branch 3 keys
    // on
    // isOnPrimitive and not merely on being a fixture.
    JavaClass offPrimitive = imported(NewThreadFixture.class);
    assertTrue(
        BackgroundEntrypointTenantScopeArchTest.staleEntries(
                Map.of(offPrimitive.getFullName(), "touches-no-tenant-table: nothing"),
                Map.of(offPrimitive.getFullName(), offPrimitive))
            .isEmpty(),
        "a concrete background class off the primitive must not be flagged stale");
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

  @Test
  @DisplayName(
      "the effective active-tables prefers the JVM system property over the production file")
  void effectiveActiveTablesPrefersTheShadowSystemProperty() {
    Set<String> file = Set.of("tags");
    // A shadow run passes -Dopenaev.tenant.active-tables='*'; that must win over the file so the
    // wildcard expiry the guard promises actually fires in that run.
    assertEquals(
        Set.of("*"),
        BackgroundEntrypointTenantScopeArchTest.resolveActiveTables("*", () -> file),
        "the system property the JVM carries is the effective allowlist and must win over the file");
    // No property set: an ordinary build reads the production application.properties.
    assertEquals(
        file,
        BackgroundEntrypointTenantScopeArchTest.resolveActiveTables(null, () -> file),
        "with no system property the production file is the source of truth");
  }

  @Test
  @DisplayName("effectiveActiveTables reads the -D the shadow run passes, not only the file")
  void effectiveActiveTablesReadsTheShadowProperty() {
    String key = BackgroundEntrypointTenantScopeArchTest.ACTIVE_TABLES_PROPERTY;
    String previous = System.getProperty(key);
    try {
      System.setProperty(key, "*");
      assertTrue(
          BackgroundEntrypointTenantScopeArchTest.effectiveActiveTables().contains("*"),
          "the shadow run's -Dopenaev.tenant.active-tables='*' must reach the expiry check");
    } finally {
      if (previous == null) {
        System.clearProperty(key);
      } else {
        System.setProperty(key, previous);
      }
    }
  }

  @Test
  @DisplayName("a v1-scoped background path names every table it reaches outside the primitive")
  void v1ScopedWaiversNameEveryTableTheyReach() {
    // These paths run under the v1 TenantContext, not the v2 primitive, so activating any table
    // they reach outside the primitive would turn their reads/writes into wrong-tenant accesses.
    // Each must carry an until-active tag for every such table so the day it activates the guard
    // fails and forces conversion. A revert to an incomplete reason fails here.
    Map<String, String> baseline = BackgroundEntrypointTenantScopeArchTest.loadBaseline();
    assertUntilActive(baseline, "io.openaev.service.EsAttackPathService", "attack_patterns");
    assertUntilActive(baseline, "io.openaev.rest.stream.StreamApi", "injects");
    assertUntilActive(
        baseline,
        "io.openaev.rest.reporting.service.PlaywrightReportingRenderer",
        "reporting_generations",
        "documents");
    assertUntilActive(
        baseline,
        "io.openaev.scheduler.jobs.reporting.ReportingScheduleJob",
        "reporting_schedules",
        "reportings",
        "reporting_generations",
        "documents");
  }

  private static void assertUntilActive(
      Map<String, String> baseline, String fqcn, String... tables) {
    String reason = baseline.get(fqcn);
    assertTrue(reason != null, fqcn + " must be listed in the baseline");
    Set<String> tagged = BackgroundEntrypointTenantScopeArchTest.untilActiveTables(reason);
    for (String table : tables) {
      assertTrue(
          tagged.contains(table),
          fqcn
              + " reaches '"
              + table
              + "' outside the primitive and must carry until-active:"
              + table
              + ", got: "
              + reason);
    }
  }
}
