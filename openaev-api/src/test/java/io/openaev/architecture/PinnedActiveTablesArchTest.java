package io.openaev.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.architecture.PinnedActiveTablesCatalog.Registration;
import io.openaev.architecture.fixture.PinnedActiveTablesFixtures;
import io.openaev.config.ProductionActiveTables;
import io.openaev.config.TenantTables;
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
 * Fails when a test class sets the activation list itself, in an annotation or through a dynamic
 * registration whose value does not hold every production table, without saying why. A class that
 * sets nothing runs on the test profile's list, which this guard does not judge. An annotation's
 * value is written once and cannot follow production, so every annotation that sets the key is a
 * pin, whatever tables it lists today.
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
 *
 * <p>Every check holds on an empty baseline with no pin left, the state the debt count is driven
 * towards. A check that the scan saw anything at all rests on classes that outlive the debt: this
 * guard and the reader it compares against, never on a number of pins.
 */
@DisplayName("A test class that pins the activation list says why")
class PinnedActiveTablesArchTest {

  /** How many lines are {@code probe} or {@code control}. Raising it is a review call. */
  static final int DELIBERATE_SIZE = 94;

  /** How many lines are {@code drifts}. Lower it, never raise it. */
  static final int DEBT_SIZE = 128;

  private static final Scan SCAN =
      Scan.of(
          PinnedActiveTablesCatalog.testTreeClasses(),
          PinnedActiveTablesBaseline.load(),
          ProductionActiveTables.names());

  @Nested
  @DisplayName("The inventory it is built on")
  class Inventory {

    @Test
    @DisplayName("given the fixture package, should find its pins through the same class scan")
    void given_fixturePackage_should_findItsPinsThroughTheSameClassScan() {
      // Arrange
      List<Class<?>> fixtures = PinnedActiveTablesCatalog.fixtureClasses();

      // Act
      Scan scan = Scan.of(fixtures, List.of(), SCAN.production());

      // Assert
      assertThat(scan.annotationPins())
          .containsEntry(
              PinnedActiveTablesFixtures.PinsThroughTestPropertySource.class.getName(),
              Set.of("documents", "tags"));
      assertThat(scan.conflicts())
          .containsKeys(
              PinnedActiveTablesFixtures.DeclaresTheKeyTwice.class.getName(),
              PinnedActiveTablesFixtures.OverridesAnInheritedPin.class.getName());
      assertThat(scan.inherited())
          .containsKeys(
              PinnedActiveTablesFixtures.InheritsThePin.class.getName(),
              PinnedActiveTablesFixtures.InheritsARegisteredPin.class.getName());
      assertThat(scan.dynamicPins())
          .containsKey(PinnedActiveTablesFixtures.SubtractsOneTable.class.getName())
          .containsKey(PinnedActiveTablesFixtures.DelegatesToAHelper.class.getName())
          .doesNotContainKey(PinnedActiveTablesFixtures.FollowsProduction.class.getName())
          .doesNotContainKey(PinnedActiveTablesFixtures.RegistersTheWildcard.class.getName());
    }

    @Test
    @DisplayName("given the test tree, should reach this guard and the reader it compares against")
    void given_testTree_should_reachThisGuardAndItsReader() {
      // Act & Assert: a scan that misses them read the wrong tree, so its silence means nothing
      assertThat(SCAN.unreached()).isEmpty();
    }
  }

  @Nested
  @DisplayName("The baseline")
  class TheBaseline {

    @Test
    @DisplayName("given the live pins, should find each one declared in the baseline")
    void given_livePins_should_allBeDeclared() {
      // Act
      List<String> undeclared = SCAN.undeclared();

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
      // Act & Assert
      assertThat(SCAN.pinnedBothWays())
          .as("Spring lets the registration win over the annotation; keep one of the two")
          .isEmpty();
    }

    @Test
    @DisplayName("given a class, should find the key set in one annotation at most")
    void given_aClass_should_findTheKeySetInOneAnnotationAtMost() {
      // Act & Assert
      assertThat(SCAN.conflicts())
          .as(
              "These classes set %s in several annotations, inherited or repeated, and Spring"
                  + " keeps one by its own precedence; declare it once",
              PinnedActiveTablesCatalog.KEY)
          .isEmpty();
    }

    @Test
    @DisplayName("given a class, should not run with a pin it inherits from a type it extends")
    void given_aClass_should_notRunWithAPinItInherits() {
      // Act & Assert
      assertThat(SCAN.inherited())
          .as(
              "These classes run with a pin declared on a class or interface they extend, whose"
                  + " baseline reason was written for that type's assertions. Follow the"
                  + " production list here, or move the pin to the class that runs")
          .isEmpty();
    }

    @Test
    @DisplayName("given the baseline, should record each class's pinned list exactly")
    void given_baseline_should_recordEachPinnedListExactly() {
      // Act & Assert
      assertThat(SCAN.mismatched())
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
      assertThat(PinnedActiveTablesBaseline.malformed(SCAN.baseline()))
          .as("every line of " + PinnedActiveTablesBaseline.RESOURCE)
          .isEmpty();
    }

    @Test
    @DisplayName("given the production list, should hold no line past its until-active table")
    void given_productionList_should_holdNoLinePastItsUntilActiveTable() {
      // Act & Assert
      assertThat(PinnedActiveTablesBaseline.expired(SCAN.baseline(), SCAN.production()))
          .as(
              "These lines rest on a table production has now activated. Re-judge each class:"
                  + " follow the production list, or rewrite the line with what it now shows")
          .isEmpty();
    }

    @Test
    @DisplayName("given the baseline, should name only activatable tables in until-active")
    void given_baseline_should_nameOnlyActivatableTablesInUntilActive() {
      // Act & Assert
      assertThat(
              PinnedActiveTablesBaseline.unknownUntilActive(
                  SCAN.baseline(),
                  BackgroundEntrypointTenantScopeArchTest.wildcardActivatedTables()))
          .as("an until-active table must be a strict tenant table, or the line never expires")
          .isEmpty();
    }

    @Test
    @DisplayName("given the terminal '*' as the production list, should expire every control line")
    void given_terminalWildcardAsProductionList_should_expireEveryControlLine() {
      // Act & Assert: '*' ends the rollout, so no mechanism a control line shows outlives it
      assertThat(SCAN.outliving(ProductionActiveTables.armedBy(TenantTables.ALL_STRICT))).isEmpty();
    }

    @Test
    @DisplayName(
        "given the baseline, should hold exactly the pinned numbers of deliberate and debt")
    void given_baseline_should_holdExactlyThePinnedCounts() {
      // Act & Assert
      assertThat(SCAN.miscounted(DELIBERATE_SIZE, DEBT_SIZE)).isEmpty();
    }
  }

  /**
   * What the guard reads from a set of classes, and what it reports against a baseline. The tests
   * above run it over the test tree; {@link PinnedActiveTablesRulesTest} runs it over fixtures.
   */
  record Scan(
      List<Class<?>> classes,
      Map<String, Set<String>> annotationPins,
      Map<String, Registration> dynamicRegistrations,
      Map<String, Registration> dynamicPins,
      List<PinnedActiveTablesBaseline.Entry> baseline,
      Set<String> production) {

    /** The classes a scan of the test tree must reach for its silence to mean anything. */
    static final List<Class<?>> REACHED =
        List.of(PinnedActiveTablesArchTest.class, ProductionActiveTables.class);

    static Scan of(
        List<Class<?>> classes,
        List<PinnedActiveTablesBaseline.Entry> baseline,
        Set<String> production) {
      Map<String, Registration> registrations =
          PinnedActiveTablesCatalog.dynamicRegistrations(classes);
      return new Scan(
          classes,
          PinnedActiveTablesCatalog.annotationPins(classes),
          registrations,
          PinnedActiveTablesCatalog.dynamicPins(registrations, production),
          baseline,
          production);
    }

    /**
     * The classes of {@link #REACHED} the scan did not see. They are this guard and the reader it
     * compares against, in two packages, so they stay in the test tree as long as the guard does: a
     * scan that misses them read the wrong tree, while a tree with no pin left still passes.
     */
    List<String> unreached() {
      Set<Class<?>> seen = Set.copyOf(classes);
      return REACHED.stream().filter(type -> !seen.contains(type)).map(Class::getName).toList();
    }

    /** Each pinning class the baseline does not declare, written as the line it would need. */
    List<String> undeclared() {
      Set<String> declared = new TreeSet<>();
      baseline.forEach(entry -> declared.add(entry.testClass()));
      List<String> undeclared = new ArrayList<>();
      livePins()
          .forEach(
              (testClass, column) -> {
                if (!declared.contains(testClass)) {
                  Registration registration = dynamicPins.get(testClass);
                  String failure =
                      registration == null || registration.evaluated()
                          ? ""
                          : " (" + registration.failure() + ")";
                  undeclared.add(testClass + " | " + column + " | <kind>: <why>" + failure);
                }
              });
      return undeclared;
    }

    Set<String> pinnedBothWays() {
      Set<String> both = new TreeSet<>(annotationPins.keySet());
      both.retainAll(dynamicRegistrations.keySet());
      return both;
    }

    Map<String, List<String>> conflicts() {
      return PinnedActiveTablesCatalog.conflictingDeclarations(classes);
    }

    Map<String, String> inherited() {
      return PinnedActiveTablesCatalog.inheritedPins(classes, dynamicPins);
    }

    /** The baseline lines that no longer describe their class, each with what changed. */
    Map<String, String> mismatched() {
      Map<String, String> live = livePins();
      Map<String, String> mismatched = new TreeMap<>();
      for (PinnedActiveTablesBaseline.Entry entry : baseline) {
        String actual = live.get(entry.testClass());
        String recorded = column(entry.tables());
        if (actual == null) {
          mismatched.put(
              entry.testClass(), "no longer runs with less than production: remove the line");
        } else if (!actual.equals(recorded)) {
          mismatched.put(entry.testClass(), "recorded " + recorded + ", runs with " + actual);
        }
      }
      return mismatched;
    }

    /**
     * The {@code control} lines an activation list arming {@code armed} would leave standing. A
     * line is selected by its kind, not by a parsed {@code until-active}, so a control whose expiry
     * failed to parse stands, and is reported, rather than drop out of the check.
     */
    List<String> outliving(Set<String> armed) {
      List<PinnedActiveTablesBaseline.Entry> controls =
          baseline.stream()
              .filter(entry -> PinnedActiveTablesBaseline.EXPIRING_KIND.equals(entry.kind()))
              .toList();
      List<String> outliving = new ArrayList<>();
      controls.stream()
          .filter(entry -> entry.untilActive().stream().noneMatch(armed::contains))
          .forEach(entry -> outliving.add("line " + entry.line() + " '" + entry.testClass() + "'"));
      return outliving;
    }

    /** What is wrong with the two counts, empty when both match their constant. */
    List<String> miscounted(int deliberateSize, int debtSize) {
      long deliberate =
          baseline.stream()
              .filter(entry -> PinnedActiveTablesBaseline.DELIBERATE_KINDS.contains(entry.kind()))
              .count();
      long debt =
          baseline.stream()
              .filter(entry -> PinnedActiveTablesBaseline.DEBT_KINDS.contains(entry.kind()))
              .count();
      List<String> miscounted = new ArrayList<>();
      if (deliberate != deliberateSize) {
        miscounted.add(
            "The probe and control lines number "
                + deliberate
                + ", not DELIBERATE_SIZE "
                + deliberateSize
                + ". Removed one? Lower the constant. Added or relabelled one? That is the review"
                + " call.");
      }
      if (debt != debtSize) {
        miscounted.add(
            "The drifts lines number "
                + debt
                + ", not DEBT_SIZE "
                + debtSize
                + ". Converted a class? Lower the constant. A new class follows the production"
                + " list instead.");
      }
      return miscounted;
    }

    /** The column each pinning class should carry, annotation and dynamic pins together. */
    private Map<String, String> livePins() {
      Map<String, String> live = new TreeMap<>();
      annotationPins.forEach((testClass, tables) -> live.put(testClass, column(tables)));
      dynamicPins.forEach(
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
}
