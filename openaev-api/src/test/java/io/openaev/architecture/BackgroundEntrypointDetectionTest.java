package io.openaev.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import io.openaev.architecture.background_fixtures.AbstractExecutorFieldParentFixture;
import io.openaev.architecture.background_fixtures.InheritedExecutorFieldFixture;
import io.openaev.architecture.background_fixtures.InheritedPostConstructFixture;
import io.openaev.architecture.background_fixtures.InlineCompletableFutureFixture;
import io.openaev.architecture.background_fixtures.InlineExecutorFixture;
import io.openaev.architecture.background_fixtures.NewThreadFixture;
import io.openaev.architecture.background_fixtures.PlainBeanFixture;
import io.openaev.architecture.background_fixtures.ScheduledOnlyFixture;
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
  @DisplayName("a plain bean belongs to no family (detection is not trivially always-true)")
  void plainBeanIsNoFamily() {
    assertEquals(
        List.of(),
        BackgroundEntrypointTenantScopeArchTest.familiesOf(imported(PlainBeanFixture.class)),
        "a class with no marker must belong to no family");
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
  @DisplayName("a dual-scope or outside-v2 table cannot be named by an until-active tag")
  void untilActiveMustNameAWildcardActivatedTable() {
    Set<String> known = BackgroundEntrypointTenantScopeArchTest.knownTenantTables();
    assertTrue(known.contains("injects"), "a strict table '*' activates must be known");
    assertTrue(known.contains("reporting_schedules"), "a strict table '*' activates must be known");
    assertTrue(
        !known.contains("groups"),
        "a dual-scope table is not activated by '*', so it must not be a legal until-active target");
    assertTrue(
        !known.contains("attackpath_graph_version"),
        "a strict table permanently outside v2 is not activated by '*', so it must not be a legal"
            + " until-active target");
    assertTrue(
        !known.contains("injcts_typo"),
        "a typo'd table name must not be a legal until-active" + " target");
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
}
