package io.openaev.architecture;

import static org.assertj.core.api.Assertions.assertThat;

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
 * Fails when a test class sets {@code openaev.tenant.active-tables} itself without saying why.
 *
 * <p>A class that pins its own list keeps testing the list it was written with. The day production
 * activates another table, that class does not arm it: whatever it asserts about a path through
 * that table runs with no isolation at all on that table, while production runs with the inspector.
 * The class stays green and its name still promises the isolation. The default for an isolation
 * test is therefore to follow production, through {@link ProductionActiveTables} in a
 * {@code @DynamicPropertySource}, adding its own tables on top when it needs more.
 *
 * <p>A class that pins on purpose declares it in {@code pinned-active-tables-baseline.txt}, with
 * the list it pins and a classified reason (see {@link PinnedActiveTablesBaseline} for the kinds).
 * The recorded list must match the class, so changing a pin brings its reason back under review.
 * The baseline size is checked for equality, and so is the number of lines that are debt, so a
 * converted class lowers the count in the same change and the debt cannot quietly regrow. A new
 * probe raises {@link #BASELINE_SIZE} in review, with its reason in the line; a new debt line is
 * never added.
 */
@DisplayName("A test class that pins the activation list says why")
class PinnedActiveTablesArchTest {

  /** The baseline's exact size. Lower it with every line removed; raising it is a review call. */
  static final int BASELINE_SIZE = 226;

  /** How many lines are {@code drifts} or {@code own-reader}. Lower it, never raise it. */
  static final int DEBT_SIZE = 17;

  private static final Map<String, Set<String>> ANNOTATION_PINS =
      PinnedActiveTablesCatalog.testTreeAnnotationPins();
  private static final Set<String> DYNAMIC_PINS = PinnedActiveTablesCatalog.testTreeDynamicPins();
  private static final List<PinnedActiveTablesBaseline.Entry> BASELINE =
      PinnedActiveTablesBaseline.load();

  @Nested
  @DisplayName("The inventory it is built on")
  class Inventory {

    @Test
    @DisplayName("given the test tree, should find the annotation pins it is known to hold")
    void given_testTree_should_findKnownAnnotationPins() {
      // Act & Assert: a floor well under what the tree holds fails a broken scan, not a shrinking
      // one
      assertThat(ANNOTATION_PINS).hasSizeGreaterThan(100);
      assertThat(ANNOTATION_PINS)
          .containsEntry(
              "io.openaev.rest.document.DocumentWriteAssociationScopeTest",
              Set.of("documents", "tags"));
    }

    @Test
    @DisplayName("given the test tree, should not report a class that reads the production list")
    void given_testTree_should_notReportAClassThatReadsTheProductionList() {
      // Act & Assert
      assertThat(DYNAMIC_PINS)
          .doesNotContain("io.openaev.rest.scenario.ScenarioDetailExercisesActiveTest");
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
      ANNOTATION_PINS.forEach(
          (testClass, tables) -> {
            if (!declared.contains(testClass)) {
              undeclared.add(testClass + " | " + column(tables) + " | <kind>: <why>");
            }
          });
      DYNAMIC_PINS.stream()
          .filter(testClass -> !declared.contains(testClass))
          .forEach(
              testClass ->
                  undeclared.add(
                      testClass + " | " + PinnedActiveTablesBaseline.DYNAMIC + " | <kind>: <why>"));

      // Assert
      assertThat(undeclared)
          .as(
              """
              These classes set %s themselves. Follow the production list instead \
              (ProductionActiveTables in a @DynamicPropertySource, plus your own tables), or, \
              if the pin is deliberate, declare it in %s:%n%s""",
              PinnedActiveTablesCatalog.KEY,
              PinnedActiveTablesBaseline.RESOURCE,
              String.join(System.lineSeparator(), undeclared))
          .isEmpty();
    }

    @Test
    @DisplayName("given the baseline, should record each class's pinned list exactly")
    void given_baseline_should_recordEachPinnedListExactly() {
      // Act
      Map<String, String> mismatched = new TreeMap<>();
      for (PinnedActiveTablesBaseline.Entry entry : BASELINE) {
        String actual = actualColumn(entry.testClass());
        String recorded =
            entry.tables() == null ? PinnedActiveTablesBaseline.DYNAMIC : column(entry.tables());
        if (actual == null) {
          mismatched.put(entry.testClass(), "pins nothing any more: remove the line");
        } else if (!actual.equals(recorded)) {
          mismatched.put(entry.testClass(), "recorded " + recorded + ", pins " + actual);
        }
      }

      // Assert
      assertThat(mismatched)
          .as(
              "Lines of %s that no longer describe their class. Re-read the reason when a pin"
                  + " changes, then fix the line, or remove it and lower BASELINE_SIZE",
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
    @DisplayName("given the baseline, should hold exactly the pinned number of lines and of debt")
    void given_baseline_should_holdExactlyThePinnedCounts() {
      // Act
      long debt =
          BASELINE.stream()
              .filter(entry -> PinnedActiveTablesBaseline.DEBT_KINDS.contains(entry.kind()))
              .count();

      // Assert
      assertThat(BASELINE.size())
          .as("Baseline size must equal BASELINE_SIZE. Removed a line? Lower the constant.")
          .isEqualTo(BASELINE_SIZE);
      assertThat(debt)
          .as(
              "The drifts and own-reader lines must number DEBT_SIZE. Converted a class? Lower"
                  + " the constant. A new class follows the production list instead.")
          .isEqualTo(DEBT_SIZE);
    }
  }

  private static String actualColumn(String testClass) {
    if (DYNAMIC_PINS.contains(testClass)) {
      return PinnedActiveTablesBaseline.DYNAMIC;
    }
    Set<String> tables = ANNOTATION_PINS.get(testClass);
    return tables == null ? null : column(tables);
  }

  private static String column(Set<String> tables) {
    return tables.isEmpty() ? PinnedActiveTablesBaseline.NO_TABLES : String.join(",", tables);
  }
}
