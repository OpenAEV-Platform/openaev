package io.openaev.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.architecture.PinnedActiveTablesCatalog.Registration;
import io.openaev.config.ProductionActiveTables;
import io.openaev.config.TenantTables;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

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

  @TestFactory
  @DisplayName("given the test tree, should pass every check of the guard")
  Stream<DynamicTest> given_testTree_should_passEveryCheckOfTheGuard() {
    Scan scan =
        Scan.of(
            PinnedActiveTablesCatalog.testTreeClasses(),
            PinnedActiveTablesBaseline.load(),
            ProductionActiveTables.names());
    return scan.checks(DELIBERATE_SIZE, DEBT_SIZE).stream()
        .map(
            check ->
                DynamicTest.dynamicTest(
                    check.name(),
                    () ->
                        assertThat(check.found().get())
                            .as("%s. %s", check.name(), check.guidance())
                            .isEmpty()));
  }

  /**
   * What the guard reads from a set of classes, and what it reports against a baseline. The test
   * above runs its checks over the test tree; {@link PinnedActiveTablesRulesTest} runs them over
   * fixtures.
   */
  record Scan(
      List<Class<?>> classes,
      Map<String, Set<String>> annotationPins,
      Map<String, Registration> dynamicRegistrations,
      Map<String, Registration> dynamicPins,
      List<PinnedActiveTablesBaseline.Entry> baseline,
      Set<String> production) {

    /**
     * One check of the guard: what it requires, what to do when it fails, and what it finds. It
     * passes when it finds nothing.
     */
    record Check(String name, String guidance, Supplier<List<String>> found) {}

    /**
     * Every check the guard runs, in the order it reports them. The guard's only test runs this
     * list over the test tree, and the end-state rules test runs the same list over an empty
     * baseline, so a check added here is held to both and a check cannot be added to the guard
     * without being added here: {@code PinnedActiveTablesRulesTest} refuses a second test method in
     * the guard.
     */
    List<Check> checks(int deliberateSize, int debtSize) {
      return List.of(
          new Check(
              "should reach this guard and the reader it compares against",
              "a scan that misses them read the wrong tree, so its silence means nothing",
              this::unreached),
          new Check(
              "should find each live pin declared in the baseline",
              """
              These classes run with %s not holding the production list. Follow it instead \
              (ProductionActiveTables in a @DynamicPropertySource, plus your own tables), or, \
              if the pin is deliberate, declare it in %s"""
                  .formatted(PinnedActiveTablesCatalog.KEY, PinnedActiveTablesBaseline.RESOURCE),
              this::undeclared),
          new Check(
              "should find each class pinned by an annotation or a registration, not both",
              "Spring lets the registration win over the annotation; keep one of the two",
              () -> List.copyOf(pinnedBothWays())),
          new Check(
              "should find the key set in one annotation at most",
              "These classes set "
                  + PinnedActiveTablesCatalog.KEY
                  + " in several annotations, inherited or repeated, and Spring keeps one by its"
                  + " own precedence; declare it once",
              () -> lines(conflicts())),
          new Check(
              "should find no class running with a pin it inherits from a type it extends",
              "These classes run with a pin declared or registered on a class or interface they"
                  + " extend, whose baseline reason was written for that type's assertions. Follow"
                  + " the production list here, or move the pin to the class that runs",
              () -> lines(inherited())),
          new Check(
              "should find each pinned list recorded exactly",
              "Lines of "
                  + PinnedActiveTablesBaseline.RESOURCE
                  + " that no longer describe their class. Re-read the reason when a pin changes,"
                  + " then fix the line, or remove it and lower its count",
              () -> lines(mismatched())),
          new Check(
              "should find a classified reason on every line",
              "every line of " + PinnedActiveTablesBaseline.RESOURCE,
              () -> PinnedActiveTablesBaseline.malformed(baseline)),
          new Check(
              "should find no line past its until-active table",
              "These lines rest on a table production has now activated. Re-judge each class:"
                  + " follow the production list, or rewrite the line with what it now shows",
              () -> PinnedActiveTablesBaseline.expired(baseline, production)),
          new Check(
              "should find only activatable tables in until-active",
              "an until-active table must be a strict tenant table, or the line never expires",
              () ->
                  PinnedActiveTablesBaseline.unknownUntilActive(
                      baseline, BackgroundEntrypointTenantScopeArchTest.wildcardActivatedTables())),
          new Check(
              "should find every control line expired by the terminal '*'",
              "'*' ends the rollout, so no mechanism a control line shows outlives it",
              () -> outliving(ProductionActiveTables.armedBy(TenantTables.ALL_STRICT))),
          new Check(
              "should find exactly the pinned numbers of deliberate and debt lines",
              "the two counts of " + PinnedActiveTablesBaseline.RESOURCE,
              () -> miscounted(deliberateSize, debtSize)));
    }

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

    private static List<String> lines(Map<String, ?> found) {
      return found.entrySet().stream()
          .map(entry -> entry.getKey() + ": " + entry.getValue())
          .toList();
    }

    private static String column(Set<String> tables) {
      if (tables == null) {
        return PinnedActiveTablesBaseline.UNEVALUATED;
      }
      return tables.isEmpty() ? PinnedActiveTablesBaseline.NO_TABLES : String.join(",", tables);
    }
  }
}
