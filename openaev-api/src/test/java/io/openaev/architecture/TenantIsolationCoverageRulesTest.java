package io.openaev.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.openaev.architecture.TenantIsolationCoverageArchTest.Effective;
import io.openaev.architecture.TenantIsolationCoverageArchTest.Scan;
import io.openaev.architecture.fixture.TenantIsolationCoverageFixtures.MarksAProductionFollower;
import io.openaev.architecture.fixture.TenantIsolationCoverageFixtures.MarksATableItsPinLeavesOut;
import io.openaev.architecture.fixture.TenantIsolationCoverageFixtures.MarksATableItsRegistrationLeavesOut;
import io.openaev.architecture.fixture.TenantIsolationCoverageFixtures.MarksATypo;
import io.openaev.architecture.fixture.TenantIsolationCoverageFixtures.MarksAnInactiveTable;
import io.openaev.architecture.fixture.TenantIsolationCoverageFixtures.MarksNothing;
import io.openaev.architecture.fixture.TenantIsolationCoverageFixtures.MarksNothingAtAll;
import io.openaev.architecture.fixture.TenantIsolationCoverageFixtures.MarksOnAProbePin;
import io.openaev.architecture.fixture.TenantIsolationCoverageFixtures.MarksOnARegistrationHoldingProduction;
import io.openaev.architecture.fixture.TenantIsolationCoverageFixtures.MarksOnARegistrationTheScanCannotEvaluate;
import io.openaev.architecture.fixture.TenantIsolationCoverageFixtures.MarksOnAnInheritedPin;
import io.openaev.architecture.fixture.TenantIsolationCoverageFixtures.MarksWithAnAnnotationAndARegistration;
import io.openaev.architecture.fixture.TenantIsolationCoverageFixtures.MarksWithTheKeyDeclaredTwice;
import io.openaev.architecture.fixture.TenantIsolationCoverageFixtures.PinsTags;
import io.openaev.config.ProductionActiveTables;
import io.openaev.config.TenantTables;
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
 * Proves the isolation coverage gate reads the effective list of a marked class the way Spring
 * does, counts only the classes that pass the bar, and rejects a baseline line nobody can trust.
 * {@link TenantIsolationCoverageArchTest} runs the same checks over the test tree. The fixtures
 * live in {@code io.openaev.architecture.fixture}, which the scan of the test tree leaves out.
 */
@DisplayName("Isolation coverage gate: it reads the marker strictly and counts only what passes")
class TenantIsolationCoverageRulesTest {

  private static final Set<String> PRODUCTION = Set.of("documents", "tags");
  private static final Set<String> SELF_ISOLATED = Set.of("members");
  private static final String GOOD_WHY =
      "the endpoint has no tenant path yet so the test cannot seed a second tenant";

  private static Scan scan(Class<?>... classes) {
    return Scan.of(List.of(classes), List.of(), List.of(), PRODUCTION, SELF_ISOLATED);
  }

  private static PinnedActiveTablesBaseline.Entry pinLine(Class<?> type, String kind) {
    return new PinnedActiveTablesBaseline.Entry(
        1, type.getName(), Set.of("tags"), kind, Set.of(), "the reason is written elsewhere");
  }

  @Nested
  @DisplayName("The effective list")
  class TheEffectiveList {

    @Test
    @DisplayName("given a class that sets nothing, should read the production list")
    void given_classThatSetsNothing_should_readTheProductionList() {
      // Act
      Effective effective =
          scan(MarksAProductionFollower.class).effective(MarksAProductionFollower.class);

      // Assert
      assertThat(effective.failure()).isNull();
      assertThat(effective.tables()).containsExactlyInAnyOrderElementsOf(PRODUCTION);
      assertThat(effective.sites()).isEmpty();
    }

    @Test
    @DisplayName("given a pin and a registration, should read the table set each one gives")
    void given_pinAndRegistration_should_readTheTableSetEachOneGives() {
      // Arrange
      Scan scan =
          scan(
              MarksOnAProbePin.class,
              MarksATableItsRegistrationLeavesOut.class,
              MarksOnARegistrationHoldingProduction.class);

      // Act and Assert
      assertThat(scan.effective(MarksOnAProbePin.class).tables())
          .containsExactlyInAnyOrder("documents", "tags");
      assertThat(scan.effective(MarksATableItsRegistrationLeavesOut.class).tables())
          .containsExactly("tags");
      assertThat(scan.effective(MarksOnARegistrationHoldingProduction.class).tables())
          .containsAll(ProductionActiveTables.names());
    }

    @Test
    @DisplayName("given a pin declared on a parent, should read it and name the parent as its site")
    void given_pinDeclaredOnAParent_should_readItAndNameTheParentAsItsSite() {
      // Act
      Effective effective =
          scan(MarksOnAnInheritedPin.class, PinsTags.class).effective(MarksOnAnInheritedPin.class);

      // Assert
      assertThat(effective.tables()).containsExactly("tags");
      assertThat(effective.sites()).containsExactly(PinsTags.class.getName());
    }

    @Test
    @DisplayName("given a list the gate cannot read, should report why instead of a list")
    void given_listTheGateCannotRead_should_reportWhyInsteadOfAList() {
      // Arrange
      Scan scan =
          scan(
              MarksWithTheKeyDeclaredTwice.class,
              MarksWithAnAnnotationAndARegistration.class,
              MarksOnARegistrationTheScanCannotEvaluate.class);

      // Act and Assert
      assertThat(scan.effective(MarksWithTheKeyDeclaredTwice.class))
          .satisfies(
              e -> {
                assertThat(e.tables()).isNull();
                assertThat(e.failure()).contains("several annotations");
              });
      assertThat(scan.effective(MarksWithAnAnnotationAndARegistration.class).failure())
          .contains("annotation and in a registration");
      assertThat(scan.effective(MarksOnARegistrationTheScanCannotEvaluate.class).failure())
          .contains("not evaluated");
    }
  }

  @Nested
  @DisplayName("The checks")
  class TheChecks {

    @Test
    @DisplayName("given markers on no table, a typo and an inactive table, should name each class")
    void given_markersOnNoTableATypoAndAnInactiveTable_should_nameEachClass() {
      // Act
      List<String> found =
          scan(
                  MarksNothing.class,
                  MarksATypo.class,
                  MarksAnInactiveTable.class,
                  MarksNothingAtAll.class)
              .markedButNotActive();

      // Assert
      assertThat(found)
          .satisfiesExactly(
              line -> assertThat(line).contains("MarksNothing").contains("names no table"),
              line -> assertThat(line).contains("MarksATypo").contains("'tagz'"),
              line -> assertThat(line).contains("MarksAnInactiveTable"));
    }

    @Test
    @DisplayName("given a self-isolated table, should refuse a marker on it and an active one")
    void given_selfIsolatedTable_should_refuseAMarkerOnItAndAnActiveOne() {
      // Arrange
      Scan scan =
          Scan.of(
              List.of(MarksAProductionFollower.class),
              List.of(),
              List.of(),
              Set.of("documents", "tags", "members"),
              Set.of("documents", "members"));

      // Act and Assert
      assertThat(scan.activeSelfIsolated()).containsExactlyInAnyOrder("documents", "members");
      assertThat(scan.markedButNotActive())
          .singleElement()
          .satisfies(line -> assertThat(line).contains("'documents'"));
    }

    @Test
    @DisplayName("given a table the effective list leaves out, should name the class and the table")
    void given_tableTheEffectiveListLeavesOut_should_nameTheClassAndTheTable() {
      // Act
      List<String> found =
          scan(
                  MarksOnAProbePin.class,
                  MarksATableItsPinLeavesOut.class,
                  MarksATableItsRegistrationLeavesOut.class,
                  MarksOnARegistrationTheScanCannotEvaluate.class)
              .markedOutsideTheEffectiveList();

      // Assert
      assertThat(found)
          .satisfiesExactly(
              line ->
                  assertThat(line).contains("MarksATableItsPinLeavesOut").contains("'documents'"),
              line ->
                  assertThat(line)
                      .contains("MarksATableItsRegistrationLeavesOut")
                      .contains("'documents'"),
              line ->
                  assertThat(line)
                      .contains("MarksOnARegistrationTheScanCannotEvaluate")
                      .contains("not evaluated"));
    }

    @Test
    @DisplayName("given a drifts or a control line, should refuse the class, a probe line is fine")
    void given_driftsOrControlLine_should_refuseTheClassAProbeLineIsFine() {
      // Arrange
      Scan scan =
          Scan.of(
              List.of(MarksOnAProbePin.class, MarksOnAnInheritedPin.class, PinsTags.class),
              List.of(),
              List.of(
                  pinLine(MarksOnAProbePin.class, "control"), pinLine(PinsTags.class, "drifts")),
              PRODUCTION,
              SELF_ISOLATED);

      // Act
      List<String> found = scan.markedButDeclaredWeaker();

      // Assert
      assertThat(found)
          .satisfiesExactly(
              line -> assertThat(line).contains("MarksOnAProbePin").contains("a control line"),
              line ->
                  assertThat(line)
                      .contains("MarksOnAnInheritedPin")
                      .contains("'" + PinsTags.class.getName() + "'")
                      .contains("a drifts line"));
      assertThat(
              Scan.of(
                      List.of(MarksOnAProbePin.class),
                      List.of(),
                      List.of(pinLine(MarksOnAProbePin.class, "probe")),
                      PRODUCTION,
                      SELF_ISOLATED)
                  .markedButDeclaredWeaker())
          .isEmpty();
    }

    @Test
    @DisplayName("given classes that fail the bar, should not count them as covering a table")
    void given_classesThatFailTheBar_should_notCountThemAsCoveringATable() {
      // Arrange
      Scan scan =
          Scan.of(
              List.of(
                  MarksOnAProbePin.class,
                  MarksATableItsPinLeavesOut.class,
                  MarksATypo.class,
                  MarksOnAnInheritedPin.class),
              List.of(),
              List.of(pinLine(PinsTags.class, "drifts")),
              PRODUCTION,
              SELF_ISOLATED);

      // Act and Assert
      assertThat(scan.covered())
          .containsOnlyKeys("tags")
          .extractingByKey("tags")
          .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
          .containsExactly(MarksOnAProbePin.class.getName());
      assertThat(scan.uncovered()).containsExactly("documents");
    }

    @Test
    @DisplayName("given a baseline line, should leave its table out of the uncovered tables")
    void given_baselineLine_should_leaveItsTableOutOfTheUncoveredTables() {
      // Arrange
      Scan scan =
          Scan.of(
              List.of(MarksOnAProbePin.class),
              TenantIsolationCoverageBaseline.parse(
                  List.of("documents | no second tenant | " + GOOD_WHY)),
              List.of(),
              PRODUCTION,
              SELF_ISOLATED);

      // Act and Assert
      assertThat(scan.uncovered()).isEmpty();
    }

    @Test
    @DisplayName("given a baseline line on a covered or an inactive table, should report it stale")
    void given_baselineLineOnACoveredOrAnInactiveTable_should_reportItStale() {
      // Arrange
      Scan scan =
          Scan.of(
              List.of(MarksOnAProbePin.class),
              TenantIsolationCoverageBaseline.parse(
                  List.of(
                      "tags | nothing now | " + GOOD_WHY,
                      "documents | no second tenant | " + GOOD_WHY,
                      "ghosts | no second tenant | " + GOOD_WHY)),
              List.of(),
              PRODUCTION,
              SELF_ISOLATED);

      // Act
      List<String> stale = scan.staleBaseline();

      // Assert
      assertThat(stale)
          .satisfiesExactly(
              line ->
                  assertThat(line).contains("'tags'").contains(MarksOnAProbePin.class.getName()),
              line -> assertThat(line).contains("'ghosts'").contains("not an active table"));
    }

    @Test
    @DisplayName("given a baseline of another size than the constant, should report both numbers")
    void given_baselineOfAnotherSizeThanTheConstant_should_reportBothNumbers() {
      // Arrange
      Scan scan =
          Scan.of(
              List.of(),
              TenantIsolationCoverageBaseline.parse(List.of("tags | nothing | " + GOOD_WHY)),
              List.of(),
              PRODUCTION,
              SELF_ISOLATED);

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
    @DisplayName("given every active table covered and an empty baseline, should report nothing")
    void given_everyActiveTableCoveredAndAnEmptyBaseline_should_reportNothing() {
      // Arrange: the gate, its marker and its reader, plus one class per kind of list
      List<Class<?>> tree =
          List.of(
              TenantIsolationCoverageArchTest.class,
              CoversTenantIsolation.class,
              ProductionActiveTables.class,
              MarksOnAProbePin.class,
              MarksAProductionFollower.class);

      // Act
      Scan scan = Scan.of(tree, List.of(), List.of(), PRODUCTION, SELF_ISOLATED);

      // Assert: the checks the gate runs, all of them, through the same method
      assertThat(scan.checks(0))
          .isNotEmpty()
          .allSatisfy(check -> assertThat(check.found().get()).as(check.name()).isEmpty());
    }

    @Test
    @DisplayName("given an active table nothing covers, should report it through the checks")
    void given_activeTableNothingCovers_should_reportItThroughTheChecks() {
      // Arrange
      List<Class<?>> tree =
          List.of(
              TenantIsolationCoverageArchTest.class,
              CoversTenantIsolation.class,
              ProductionActiveTables.class,
              MarksATableItsPinLeavesOut.class);

      // Act
      Scan scan = Scan.of(tree, List.of(), List.of(), PRODUCTION, SELF_ISOLATED);

      // Assert
      assertThat(scan.checks(0))
          .filteredOn(check -> !check.found().get().isEmpty())
          .extracting(Scan.Check::name)
          .containsExactly(
              "should find each marked table in the effective list of its class",
              "should find every active table covered or named in the baseline");
    }

    @Test
    @DisplayName("given the real production list, should hold no self-isolated table")
    void given_realProductionList_should_holdNoSelfIsolatedTable() {
      // Act
      Scan scan =
          Scan.of(
              List.of(),
              List.of(),
              List.of(),
              ProductionActiveTables.names(),
              TenantTables.selfIsolatedTables());

      // Assert
      assertThat(scan.activeSelfIsolated()).isEmpty();
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
    @DisplayName("given a scan that missed the gate, the marker and the reader, should report all")
    void given_scanThatMissedTheGateTheMarkerAndTheReader_should_reportAll() {
      // Act
      Scan scan = scan(MarksNothingAtAll.class);

      // Assert
      assertThat(scan.unreached())
          .containsExactlyInAnyOrder(
              TenantIsolationCoverageArchTest.class.getName(),
              CoversTenantIsolation.class.getName(),
              ProductionActiveTables.class.getName());
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
