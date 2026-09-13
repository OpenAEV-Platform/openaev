package io.openaev.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
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
    Map<String, String> baseline = Map.of("io.openaev.Fixture", "touches-x; until-active:injects");
    assertTrue(
        BackgroundEntrypointTenantScopeArchTest.expiredWaivers(baseline, Set.of("tags", "injects"))
            .stream()
            .anyMatch(s -> s.contains("io.openaev.Fixture")),
        "the waiver must expire once 'injects' is active");
    assertTrue(
        BackgroundEntrypointTenantScopeArchTest.expiredWaivers(baseline, Set.of("tags")).isEmpty(),
        "the same waiver must NOT expire while 'injects' is still v1 (red for the right reason)");
  }

  @Test
  @DisplayName("a waiver expires under the '*' wildcard active list (the rollout's terminal state)")
  void waiverExpiresUnderWildcard() {
    Map<String, String> baseline = Map.of("io.openaev.Fixture", "until-active:reporting_schedules");
    assertTrue(
        BackgroundEntrypointTenantScopeArchTest.expiredWaivers(baseline, Set.of("*")).stream()
            .anyMatch(s -> s.contains("io.openaev.Fixture")),
        "'*' activates every strict table, so every until-active waiver must expire");
  }

  @Test
  @DisplayName(
      "an until-active tag naming an unknown table is rejected (a waiver that never expires)")
  void unknownUntilActiveTableIsRejected() {
    Set<String> known = BackgroundEntrypointTenantScopeArchTest.knownTenantTables();
    assertTrue(known.contains("injects"), "the entity model must yield the real table 'injects'");
    assertTrue(
        known.contains("reporting_schedules"),
        "the entity model must yield the real table 'reporting_schedules'");
    assertTrue(
        !known.contains("injcts_typo"),
        "a typo'd table name must not be a known tenant table, so the guard can reject it");
  }
}
