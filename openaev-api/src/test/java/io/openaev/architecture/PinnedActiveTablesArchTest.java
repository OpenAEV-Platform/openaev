package io.openaev.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.architecture.PinnedActiveTablesCatalog.Registration;
import io.openaev.architecture.fixture.PinnedActiveTablesFixtures;
import io.openaev.config.ProductionActiveTables;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Fails when a test class runs with an activation list that does not hold the production one,
 * without saying why.
 *
 * <p>A class that pins its own list keeps testing the list it was written with. The day production
 * activates another table, that class does not arm it: whatever it asserts about a path through
 * that table runs with no isolation at all on that table, while production runs with the inspector.
 * The class stays green and its name still promises the isolation. The default for a test is
 * therefore to follow production, through {@link ProductionActiveTables} in a {@code
 * DynamicPropertySource}, adding its own tables on top when it needs more. Such a registration is
 * judged by the value it registers, so it follows production only while that value holds every
 * production table.
 *
 * <p>A class that pins on purpose declares it in {@code pinned-active-tables-baseline.txt}, with
 * the list it runs with and a classified reason (see {@link PinnedActiveTablesBaseline} for the
 * kinds). The recorded list must match the class, annotation or dynamic, so changing a pin brings
 * its reason back under review. The number of deliberate lines and the number of debt lines are
 * each checked for equality, so a converted class lowers the debt count in the same change, a line
 * relabelled from debt to deliberate moves both counts, and the debt cannot quietly regrow. A new
 * deliberate line raises {@link #DELIBERATE_SIZE} in review, with its reason in the line; a new
 * debt line is never added.
 */
@DisplayName("A test class that pins the activation list says why")
class PinnedActiveTablesArchTest {

  /** How many lines are {@code probe} or {@code control}. Raising it is a review call. */
  static final int DELIBERATE_SIZE = 185;

  /** How many lines are {@code drifts}. Lower it, never raise it. */
  static final int DEBT_SIZE = 37;

  private static final Set<String> PRODUCTION = ProductionActiveTables.names();
  private static final List<Class<?>> TEST_TREE = PinnedActiveTablesCatalog.testTreeClasses();
  private static final Map<String, Set<String>> ANNOTATION_PINS =
      PinnedActiveTablesCatalog.annotationPins(TEST_TREE);
  private static final Map<String, Registration> DYNAMIC_REGISTRATIONS =
      PinnedActiveTablesCatalog.dynamicRegistrations(TEST_TREE);
  private static final Map<String, Registration> DYNAMIC_PINS =
      PinnedActiveTablesCatalog.dynamicPins(DYNAMIC_REGISTRATIONS, PRODUCTION);
  private static final List<PinnedActiveTablesBaseline.Entry> BASELINE =
      PinnedActiveTablesBaseline.load();

  @Nested
  @DisplayName("The inventory it is built on")
  class Inventory {

    @Test
    @DisplayName("given the fixture package, should find its pins through the same class scan")
    void given_fixturePackage_should_findItsPinsThroughTheSameClassScan() {
      // Arrange
      List<Class<?>> fixtures = PinnedActiveTablesCatalog.fixtureClasses();

      // Act
      Map<String, Set<String>> annotationPins = PinnedActiveTablesCatalog.annotationPins(fixtures);
      Map<String, Registration> dynamicPins =
          PinnedActiveTablesCatalog.dynamicPins(
              PinnedActiveTablesCatalog.dynamicRegistrations(fixtures), PRODUCTION);

      // Assert
      assertThat(annotationPins)
          .containsEntry(
              PinnedActiveTablesFixtures.PinsThroughTestPropertySource.class.getName(),
              Set.of("documents", "tags"));
      assertThat(dynamicPins)
          .containsKey(PinnedActiveTablesFixtures.SubtractsOneTable.class.getName())
          .doesNotContainKey(PinnedActiveTablesFixtures.FollowsProduction.class.getName());
    }

    @Test
    @DisplayName("given the test tree, should find the pins and the registrations it holds")
    void given_testTree_should_findThePinsAndTheRegistrationsItHolds() {
      // Act & Assert: floors well under what the tree holds fail a broken scan, not a shrinking
      // one
      assertThat(ANNOTATION_PINS).hasSizeGreaterThan(100);
      assertThat(DYNAMIC_REGISTRATIONS).hasSizeGreaterThan(10);
    }
  }

  @Nested
  @DisplayName("The baseline")
  class TheBaseline {

    @Test
    @DisplayName("given the live pins, should find each one declared in the baseline")
    void given_livePins_should_allBeDeclared() {
      // Arrange
      Set<String> declared = new TreeSet<>();
      BASELINE.forEach(entry -> declared.add(entry.testClass()));

      // Act
      List<String> undeclared = new ArrayList<>();
      livePins()
          .forEach(
              (testClass, column) -> {
                if (!declared.contains(testClass)) {
                  Registration registration = DYNAMIC_PINS.get(testClass);
                  String failure =
                      registration == null || registration.evaluated()
                          ? ""
                          : " (" + registration.failure() + ")";
                  undeclared.add(testClass + " | " + column + " | <kind>: <why>" + failure);
                }
              });

      // Assert
      assertThat(undeclared)
          .as(
              """
              These classes run with %s not holding the production list. Follow it instead \
              (ProductionActiveTables in a @DynamicPropertySource, plus your own tables), or, \
              if the pin is deliberate, declare it in %s:%n%s""",
              PinnedActiveTablesCatalog.KEY,
              PinnedActiveTablesBaseline.RESOURCE,
              String.join(System.lineSeparator(), undeclared))
          .isEmpty();
    }

    @Test
    @DisplayName(
        "given a class, should find it pinned by an annotation or a registration, not both")
    void given_aClass_should_pinByAnAnnotationOrARegistrationNotBoth() {
      // Act
      Set<String> both = new TreeSet<>(ANNOTATION_PINS.keySet());
      both.retainAll(DYNAMIC_REGISTRATIONS.keySet());

      // Assert
      assertThat(both)
          .as("Spring lets the registration win over the annotation; keep one of the two")
          .isEmpty();
    }

    @Test
    @DisplayName("given the baseline, should record each class's pinned list exactly")
    void given_baseline_should_recordEachPinnedListExactly() {
      // Arrange
      Map<String, String> live = livePins();

      // Act
      Map<String, String> mismatched = new TreeMap<>();
      for (PinnedActiveTablesBaseline.Entry entry : BASELINE) {
        String actual = live.get(entry.testClass());
        String recorded = column(entry.tables());
        if (actual == null) {
          mismatched.put(
              entry.testClass(), "no longer runs with less than production: remove the line");
        } else if (!actual.equals(recorded)) {
          mismatched.put(entry.testClass(), "recorded " + recorded + ", runs with " + actual);
        }
      }

      // Assert
      assertThat(mismatched)
          .as(
              "Lines of %s that no longer describe their class. Re-read the reason when a pin"
                  + " changes, then fix the line, or remove it and lower its count",
              PinnedActiveTablesBaseline.RESOURCE)
          .isEmpty();
    }

    @Test
    @DisplayName("given the baseline, should carry a classified reason on every line")
    void given_baseline_should_carryAClassifiedReasonOnEveryLine() {
      // Act & Assert
      assertThat(PinnedActiveTablesBaseline.malformed(BASELINE))
          .as("every line of " + PinnedActiveTablesBaseline.RESOURCE)
          .isEmpty();
    }

    @Test
    @DisplayName("given the production list, should hold no line past its until-active table")
    void given_productionList_should_holdNoLinePastItsUntilActiveTable() {
      // Act & Assert
      assertThat(PinnedActiveTablesBaseline.expired(BASELINE, PRODUCTION))
          .as(
              "These lines rest on a table production has now activated. Re-judge each class:"
                  + " follow the production list, or rewrite the line with what it now shows")
          .isEmpty();
    }

    @Test
    @DisplayName(
        "given the baseline, should hold exactly the pinned numbers of deliberate and debt")
    void given_baseline_should_holdExactlyThePinnedCounts() {
      // Act
      long deliberate =
          BASELINE.stream()
              .filter(entry -> PinnedActiveTablesBaseline.DELIBERATE_KINDS.contains(entry.kind()))
              .count();
      long debt =
          BASELINE.stream()
              .filter(entry -> PinnedActiveTablesBaseline.DEBT_KINDS.contains(entry.kind()))
              .count();

      // Assert
      assertThat(deliberate)
          .as(
              "The probe and control lines must number DELIBERATE_SIZE. Removed one? Lower the"
                  + " constant. Added or relabelled one? That is the review call.")
          .isEqualTo(DELIBERATE_SIZE);
      assertThat(debt)
          .as(
              "The drifts lines must number DEBT_SIZE. Converted a class? Lower the constant. A"
                  + " new class follows the production list instead.")
          .isEqualTo(DEBT_SIZE);
    }
  }

  /** The column each pinning class should carry, annotation and dynamic pins together. */
  private static Map<String, String> livePins() {
    Map<String, String> live = new TreeMap<>();
    ANNOTATION_PINS.forEach((testClass, tables) -> live.put(testClass, column(tables)));
    DYNAMIC_PINS.forEach(
        (testClass, registration) -> live.put(testClass, column(registration.tables())));
    return live;
  }

  private static String column(Set<String> tables) {
    if (tables == null) {
      return PinnedActiveTablesBaseline.UNEVALUATED;
    }
    return tables.isEmpty() ? PinnedActiveTablesBaseline.NO_TABLES : String.join(",", tables);
  }
}
