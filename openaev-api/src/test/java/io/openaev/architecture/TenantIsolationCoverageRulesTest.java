package io.openaev.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.openaev.architecture.TenantIsolationCoverageArchTest.Scan;
import io.openaev.architecture.fixture.TenantIsolationCoverageFixtures.DisablesAClassAroundAMarkedNestedClass;
import io.openaev.architecture.fixture.TenantIsolationCoverageFixtures.MarksAClassUnderADisabledParent;
import io.openaev.architecture.fixture.TenantIsolationCoverageFixtures.MarksAClassWhoseOnlyNestedTestIsDisabled;
import io.openaev.architecture.fixture.TenantIsolationCoverageFixtures.MarksAClassWhoseOnlyNestedTestIsPrivate;
import io.openaev.architecture.fixture.TenantIsolationCoverageFixtures.MarksAClassWhoseOnlyTestIsABareTestable;
import io.openaev.architecture.fixture.TenantIsolationCoverageFixtures.MarksAClassWhoseOnlyTestIsAComposedAnnotation;
import io.openaev.architecture.fixture.TenantIsolationCoverageFixtures.MarksAClassWhoseOnlyTestIsAFactory;
import io.openaev.architecture.fixture.TenantIsolationCoverageFixtures.MarksAClassWhoseOnlyTestIsDisabled;
import io.openaev.architecture.fixture.TenantIsolationCoverageFixtures.MarksAClassWhoseOnlyTestIsInherited;
import io.openaev.architecture.fixture.TenantIsolationCoverageFixtures.MarksAClassWhoseOnlyTestIsParameterized;
import io.openaev.architecture.fixture.TenantIsolationCoverageFixtures.MarksAClassWhoseOnlyTestIsPrivate;
import io.openaev.architecture.fixture.TenantIsolationCoverageFixtures.MarksAClassWhoseOnlyTestIsProtected;
import io.openaev.architecture.fixture.TenantIsolationCoverageFixtures.MarksAClassWhoseOnlyTestIsRepeated;
import io.openaev.architecture.fixture.TenantIsolationCoverageFixtures.MarksAClassWhoseOnlyTestIsStatic;
import io.openaev.architecture.fixture.TenantIsolationCoverageFixtures.MarksAClassWhoseOnlyTestsAreInADisabledNestedClass;
import io.openaev.architecture.fixture.TenantIsolationCoverageFixtures.MarksAClassWhoseTestsAreNested;
import io.openaev.architecture.fixture.TenantIsolationCoverageFixtures.MarksAClassWithNoTestMethod;
import io.openaev.architecture.fixture.TenantIsolationCoverageFixtures.MarksADisabledClass;
import io.openaev.architecture.fixture.TenantIsolationCoverageFixtures.MarksATypo;
import io.openaev.architecture.fixture.TenantIsolationCoverageFixtures.MarksAnAbstractClass;
import io.openaev.architecture.fixture.TenantIsolationCoverageFixtures.MarksAnUngatedTable;
import io.openaev.architecture.fixture.TenantIsolationCoverageFixtures.MarksDocuments;
import io.openaev.architecture.fixture.TenantIsolationCoverageFixtures.MarksNothing;
import io.openaev.architecture.fixture.TenantIsolationCoverageFixtures.MarksNothingAtAll;
import io.openaev.architecture.fixture.TenantIsolationCoverageFixtures.MarksTags;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.annotation.Testable;
import org.springframework.core.annotation.MergedAnnotations;

/**
 * Proves the isolation coverage gate reads the marker strictly, counts only the runnable classes
 * that name gated tables, and rejects a baseline line nobody can trust. {@link
 * TenantIsolationCoverageArchTest} runs the same checks over the test tree. The fixtures live in
 * {@code io.openaev.architecture.fixture}, which the scan of the test tree leaves out.
 */
@DisplayName("Isolation coverage gate: it reads the marker strictly and counts only what passes")
class TenantIsolationCoverageRulesTest {

  private static final Set<String> GATED = Set.of("documents", "tags");
  private static final String GOOD_WHY =
      "the endpoint has no tenant path yet so the test cannot seed a second tenant";

  private static Scan scan(Class<?>... classes) {
    return new Scan(List.of(classes), List.of(), GATED);
  }

  @Nested
  @DisplayName("The checks")
  class TheChecks {

    @Test
    @DisplayName("given markers on no table, a typo and an ungated table, should name each class")
    void given_markersOnNoTableATypoAndAnUngatedTable_should_nameEachClass() {
      // Act
      List<String> found =
          scan(
                  MarksNothing.class,
                  MarksATypo.class,
                  MarksAnUngatedTable.class,
                  MarksNothingAtAll.class)
              .markedButNotGated();

      // Assert
      assertThat(found)
          .satisfiesExactly(
              line -> assertThat(line).contains("MarksNothing").contains("names no table"),
              line -> assertThat(line).contains("MarksATypo").contains("'tagz'"),
              line -> assertThat(line).contains("MarksAnUngatedTable"));
    }

    @Test
    @DisplayName("given classes that fail the bar, should not count them as covering a table")
    void given_classesThatFailTheBar_should_notCountThemAsCoveringATable() {
      // Arrange
      Scan scan = scan(MarksTags.class, MarksATypo.class, MarksAnAbstractClass.class);

      // Act and Assert
      assertThat(scan.covered())
          .containsOnlyKeys("tags")
          .extractingByKey("tags")
          .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
          .containsExactly(MarksTags.class.getName());
      assertThat(scan.uncovered()).containsExactly("documents");
    }

    @Test
    @DisplayName(
        "given an abstract, a disabled and a testless class, should name each and not count")
    void given_abstractDisabledAndTestlessClass_should_nameEachAndNotCount() {
      // Arrange
      Scan scan =
          scan(
              MarksAnAbstractClass.class,
              MarksADisabledClass.class,
              MarksAClassWithNoTestMethod.class,
              MarksAClassWhoseOnlyTestsAreInADisabledNestedClass.class);

      // Act and Assert
      assertThat(scan.unrunnable())
          .containsEntry(MarksAnAbstractClass.class, "is abstract")
          .containsEntry(MarksADisabledClass.class, "is @Disabled, or nested in a disabled class")
          .containsEntry(MarksAClassWithNoTestMethod.class, "has no enabled test method")
          .containsEntry(
              MarksAClassWhoseOnlyTestsAreInADisabledNestedClass.class,
              "has no enabled test method");
      assertThat(scan.markedButNotRunnable()).hasSize(4);
      assertThat(scan.covered()).isEmpty();
    }

    @Test
    @DisplayName("given a marked class nested in a disabled class, should name it and not count")
    void given_markedClassNestedInADisabledClass_should_nameItAndNotCount() {
      // Arrange
      Scan scan = scan(DisablesAClassAroundAMarkedNestedClass.MarksUnderADisabledOuterClass.class);

      // Act and Assert
      assertThat(scan.unrunnable())
          .containsEntry(
              DisablesAClassAroundAMarkedNestedClass.MarksUnderADisabledOuterClass.class,
              "is @Disabled, or nested in a disabled class");
      assertThat(scan.covered()).isEmpty();
    }

    @Test
    @DisplayName("given only disabled test methods, should name the class and not count it")
    void given_onlyDisabledTestMethods_should_nameTheClassAndNotCountIt() {
      // Arrange
      Scan scan =
          scan(
              MarksAClassWhoseOnlyTestIsDisabled.class,
              MarksAClassWhoseOnlyNestedTestIsDisabled.class);

      // Act and Assert
      assertThat(scan.unrunnable())
          .containsEntry(MarksAClassWhoseOnlyTestIsDisabled.class, "has no enabled test method")
          .containsEntry(
              MarksAClassWhoseOnlyNestedTestIsDisabled.class, "has no enabled test method");
      assertThat(scan.covered()).isEmpty();
    }

    @Test
    @DisplayName(
        "given only a private, a static or a bare @Testable method, should name each class and"
            + " not count it")
    void given_onlyMethodsJupiterDoesNotRun_should_nameEachClassAndNotCountIt() {
      // Arrange
      Scan scan =
          scan(
              MarksAClassWhoseOnlyTestIsPrivate.class,
              MarksAClassWhoseOnlyTestIsStatic.class,
              MarksAClassWhoseOnlyTestIsABareTestable.class,
              MarksAClassWhoseOnlyNestedTestIsPrivate.class);

      // Act and Assert
      assertThat(scan.unrunnable())
          .containsOnlyKeys(
              MarksAClassWhoseOnlyTestIsPrivate.class,
              MarksAClassWhoseOnlyTestIsStatic.class,
              MarksAClassWhoseOnlyTestIsABareTestable.class,
              MarksAClassWhoseOnlyNestedTestIsPrivate.class)
          .containsValue("has no enabled test method");
      assertThat(scan.markedButNotRunnable()).hasSize(4);
      assertThat(scan.covered()).isEmpty();
    }

    @Test
    @DisplayName(
        "given a composed, factory, parameterized, repeated or protected test, should count the"
            + " class as runnable")
    void given_testJupiterRuns_should_countTheClassAsRunnable() {
      // Arrange
      Scan scan =
          scan(
              MarksAClassWhoseOnlyTestIsAComposedAnnotation.class,
              MarksAClassWhoseOnlyTestIsAFactory.class,
              MarksAClassWhoseOnlyTestIsParameterized.class,
              MarksAClassWhoseOnlyTestIsRepeated.class,
              MarksAClassWhoseOnlyTestIsProtected.class);

      // Act and Assert
      assertThat(scan.unrunnable()).isEmpty();
      assertThat(scan.covered().get("tags")).hasSize(5);
    }

    @Test
    @DisplayName(
        "given a test inherited from an abstract parent or a disabled parent, should count the"
            + " class as runnable")
    void given_inheritedTestOrDisabledParent_should_countTheClassAsRunnable() {
      // Arrange
      Scan scan =
          scan(MarksAClassWhoseOnlyTestIsInherited.class, MarksAClassUnderADisabledParent.class);

      // Act and Assert
      assertThat(scan.unrunnable()).isEmpty();
      assertThat(scan.covered()).containsOnlyKeys("tags");
      assertThat(scan.covered().get("tags"))
          .containsExactlyInAnyOrder(
              MarksAClassWhoseOnlyTestIsInherited.class.getName(),
              MarksAClassUnderADisabledParent.class.getName());
    }

    @Test
    @DisplayName("given tests held by a nested class, should count the class as runnable")
    void given_testsHeldByANestedClass_should_countTheClassAsRunnable() {
      // Arrange
      Scan scan = scan(MarksAClassWhoseTestsAreNested.class);

      // Act and Assert
      assertThat(scan.unrunnable()).isEmpty();
      assertThat(scan.covered()).containsOnlyKeys("tags");
    }

    @Test
    @DisplayName("given a baseline line, should leave its table out of the uncovered tables")
    void given_baselineLine_should_leaveItsTableOutOfTheUncoveredTables() {
      // Arrange
      Scan scan =
          new Scan(
              List.of(MarksTags.class),
              TenantIsolationCoverageBaseline.parse(
                  List.of("documents | no second tenant | " + GOOD_WHY)),
              GATED);

      // Act and Assert
      assertThat(scan.uncovered()).isEmpty();
    }

    @Test
    @DisplayName("given a baseline line on a covered or an ungated table, should report it stale")
    void given_baselineLineOnACoveredOrAnUngatedTable_should_reportItStale() {
      // Arrange
      Scan scan =
          new Scan(
              List.of(MarksTags.class),
              TenantIsolationCoverageBaseline.parse(
                  List.of(
                      "tags | nothing now | " + GOOD_WHY,
                      "documents | no second tenant | " + GOOD_WHY,
                      "ghosts | no second tenant | " + GOOD_WHY)),
              GATED);

      // Act
      List<String> stale = scan.staleBaseline();

      // Assert
      assertThat(stale)
          .satisfiesExactly(
              line -> assertThat(line).contains("'tags'").contains(MarksTags.class.getName()),
              line -> assertThat(line).contains("'ghosts'").contains("not a gated table"));
    }

    @Test
    @DisplayName("given a baseline of another size than the constant, should report both numbers")
    void given_baselineOfAnotherSizeThanTheConstant_should_reportBothNumbers() {
      // Arrange
      Scan scan =
          new Scan(
              List.of(),
              TenantIsolationCoverageBaseline.parse(List.of("tags | nothing | " + GOOD_WHY)),
              GATED);

      // Act and Assert
      assertThat(scan.miscounted(1)).isEmpty();
      assertThat(scan.miscounted(2))
          .singleElement()
          .satisfies(
              line -> assertThat(line).contains("names 1 tables").contains("BASELINE_SIZE 2"));
      assertThat(scan.miscounted(0)).hasSize(1);
    }
  }

  @Nested
  @DisplayName("The baseline")
  class TheBaseline {

    @Test
    @DisplayName("given well-formed lines, should parse table, missing and why")
    void given_wellFormedLines_should_parseTableMissingAndWhy() {
      // Act
      List<TenantIsolationCoverageBaseline.Entry> entries =
          TenantIsolationCoverageBaseline.parse(
              List.of(
                  "# a comment",
                  "",
                  "secrets | a second tenant | " + GOOD_WHY + " | with a pipe in it"));

      // Assert
      assertThat(entries)
          .extracting(
              TenantIsolationCoverageBaseline.Entry::table,
              TenantIsolationCoverageBaseline.Entry::missing)
          .containsExactly(tuple("secrets", "a second tenant"));
      assertThat(entries.get(0).why()).endsWith("with a pipe in it");
      assertThat(TenantIsolationCoverageBaseline.malformed(entries)).isEmpty();
    }

    @Test
    @DisplayName("given lines nobody can trust, should name each problem")
    void given_untrustworthyLines_should_nameEachProblem() {
      // Arrange
      List<TenantIsolationCoverageBaseline.Entry> entries =
          TenantIsolationCoverageBaseline.parse(
              List.of(
                  "NotATable | missing | " + GOOD_WHY,
                  "NoMissing",
                  "noreason | a second tenant | ",
                  "shortwhy | a second tenant | too short",
                  "emptymissing |  | " + GOOD_WHY,
                  "dup | a second tenant | " + GOOD_WHY,
                  "dup | a second tenant | " + GOOD_WHY));

      // Act
      List<String> problems = TenantIsolationCoverageBaseline.malformed(entries);

      // Assert
      assertThat(problems)
          .hasSize(6)
          .anySatisfy(p -> assertThat(p).contains("NotATable"))
          .anySatisfy(p -> assertThat(p).contains("NoMissing"))
          .anySatisfy(p -> assertThat(p).contains("noreason").contains("why"))
          .anySatisfy(p -> assertThat(p).contains("shortwhy"))
          .anySatisfy(p -> assertThat(p).contains("emptymissing"))
          .anySatisfy(p -> assertThat(p).contains("duplicates"));
    }
  }

  @Nested
  @DisplayName("The end state")
  class TheEndState {

    @Test
    @DisplayName("given every gated table covered and an empty baseline, should report nothing")
    void given_everyGatedTableCoveredAndAnEmptyBaseline_should_reportNothing() {
      // Arrange: the gate and its marker, plus one class per gated table
      List<Class<?>> tree =
          List.of(
              TenantIsolationCoverageArchTest.class,
              CoversTenantIsolation.class,
              MarksTags.class,
              MarksDocuments.class);

      // Act
      Scan scan = new Scan(tree, List.of(), GATED);

      // Assert: the checks the gate runs, all of them, through the same method
      assertThat(scan.checks(0))
          .isNotEmpty()
          .allSatisfy(check -> assertThat(check.found().get()).as(check.name()).isEmpty());
    }

    @Test
    @DisplayName("given a tree that breaks every rule, should fail each of the seven checks")
    void given_treeThatBreaksEveryRule_should_failEachOfTheSevenChecks() {
      // Arrange: each class and line trips one check, and nothing else covers documents
      Scan scan =
          new Scan(
              List.of(MarksATypo.class, MarksAnAbstractClass.class),
              TenantIsolationCoverageBaseline.parse(
                  List.of("tags | nothing | short", "ghosts | nothing | " + GOOD_WHY)),
              GATED);

      // Act
      List<Scan.Check> checks = scan.checks(0);

      // Assert: seven checks, each failing on its own finding, in the order the gate reports them
      assertThat(checks).hasSize(7);
      String[] findingOf = {
        TenantIsolationCoverageArchTest.class.getName(),
        "tagz",
        "MarksAnAbstractClass",
        "'tags'",
        "documents",
        "ghosts",
        "BASELINE_SIZE 0"
      };
      for (int i = 0; i < checks.size(); i++) {
        String expected = findingOf[i];
        assertThat(checks.get(i).found().get())
            .as(checks.get(i).name())
            .anySatisfy(line -> assertThat(line).contains(expected));
      }
    }

    @Test
    @DisplayName("given a gated table nothing covers, should report it through the checks")
    void given_gatedTableNothingCovers_should_reportItThroughTheChecks() {
      // Arrange
      List<Class<?>> tree =
          List.of(
              TenantIsolationCoverageArchTest.class, CoversTenantIsolation.class, MarksTags.class);

      // Act
      Scan scan = new Scan(tree, List.of(), GATED);

      // Assert
      assertThat(scan.checks(0))
          .filteredOn(check -> !check.found().get().isEmpty())
          .extracting(Scan.Check::name)
          .containsExactly("should find every gated table covered or named in the baseline");
    }

    @Test
    @DisplayName("given the gate, should find one test, the one running the checks of the scan")
    void given_theGate_should_findOneTestTheOneRunningTheChecksOfTheScan() {
      // Act
      List<String> tests = new ArrayList<>();
      collectTests(TenantIsolationCoverageArchTest.class, tests);

      // Assert: a second test could assert on the tree what the end state never runs
      assertThat(tests)
          .containsExactly(
              TenantIsolationCoverageArchTest.class.getName()
                  + ".given_testTree_should_passEveryCheckOfTheGate");
    }

    @Test
    @DisplayName("given a scan that missed the gate and the marker, should report both")
    void given_scanThatMissedTheGateAndTheMarker_should_reportBoth() {
      // Act
      Scan scan = scan(MarksNothingAtAll.class);

      // Assert
      assertThat(scan.unreached())
          .containsExactlyInAnyOrder(
              TenantIsolationCoverageArchTest.class.getName(),
              CoversTenantIsolation.class.getName());
    }
  }

  /** Every method JUnit would run as a test, in the class and its nested classes. */
  private static void collectTests(Class<?> type, List<String> tests) {
    for (Method method : type.getDeclaredMethods()) {
      if (MergedAnnotations.from(method).isPresent(Testable.class)) {
        tests.add(type.getName() + "." + method.getName());
      }
    }
    for (Class<?> nested : type.getDeclaredClasses()) {
      collectTests(nested, tests);
    }
  }
}
