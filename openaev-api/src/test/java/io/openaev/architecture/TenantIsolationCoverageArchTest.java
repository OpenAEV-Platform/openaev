package io.openaev.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.architecture.PinnedActiveTablesCatalog.Declaration;
import io.openaev.architecture.PinnedActiveTablesCatalog.Registration;
import io.openaev.config.ProductionActiveTables;
import io.openaev.config.TenantTables;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
import org.springframework.test.context.TestContextAnnotationUtils;

/**
 * Fails the build when a table of the production {@code openaev.tenant.active-tables} list has no
 * isolation test. A table is covered by at least one class marked {@link CoversTenantIsolation}
 * that runs on the real stack with the table in its effective activation list, seeds two tenants
 * and asserts a cross-tenant refusal. The gate checks the marker and the effective list; the two
 * tenants and the refusal rest on the reading of whoever adds the marker (see the marker's
 * Javadoc).
 *
 * <p>The effective list of a marked class is read with the catalog of {@link
 * PinnedActiveTablesArchTest}: the one annotation that sets the key ({@link
 * PinnedActiveTablesCatalog#declarations}), or what its {@code DynamicPropertySource} methods
 * register ({@link PinnedActiveTablesCatalog#dynamicRegistrations}), on the class, its supertypes
 * and its enclosing classes. A class that sets the key twice, both ways, or in a registration the
 * scan cannot evaluate has no list the gate can read and covers nothing. A class that sets nothing
 * is counted as running the production list through the CI arming of {@code
 * -Dopenaev.tenant.active-tables}, which this gate cannot verify.
 *
 * <p>A class whose pin the baseline of {@link PinnedActiveTablesBaseline} records as {@code drifts}
 * or {@code control} covers nothing: its list is known to differ from production for a reason that
 * makes its assertions weaker than production's.
 *
 * <p>The tables that no class covers yet are named, with what is missing and why, in {@code
 * tenant-isolation-coverage-baseline.txt}. That list only shrinks: a line whose table becomes
 * covered or inactive fails, and its size is frozen by {@link #BASELINE_SIZE}.
 */
@DisplayName("Every active table has a class marked as its isolation test")
class TenantIsolationCoverageArchTest {

  /** How many tables the baseline names. Lower it when a line goes, never raise it. */
  static final int BASELINE_SIZE = 28;

  @TestFactory
  @DisplayName("given the test tree, should pass every check of the gate")
  Stream<DynamicTest> given_testTree_should_passEveryCheckOfTheGate() {
    Scan scan =
        Scan.of(
            PinnedActiveTablesCatalog.testTreeClasses(),
            TenantIsolationCoverageBaseline.load(),
            PinnedActiveTablesBaseline.load(),
            ProductionActiveTables.names(),
            TenantTables.selfIsolatedTables());
    return scan.checks(BASELINE_SIZE).stream()
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
   * What a marked class runs with: the tables of its activation list, or why the gate cannot read
   * it. {@code sites} are the classes that decide the list, the ones the pinned-list baseline may
   * name; empty when the class follows the production list. {@code tables} is null exactly when
   * {@code failure} is set.
   */
  record Effective(Set<String> tables, String failure, Set<String> sites) {

    static Effective of(Set<String> tables, Set<String> sites) {
      return new Effective(tables, null, sites);
    }

    static Effective unreadable(String failure) {
      return new Effective(null, failure, Set.of());
    }
  }

  /**
   * What the gate reads from a set of classes, and what it reports against two baselines. The test
   * above runs its checks over the test tree; {@link TenantIsolationCoverageRulesTest} runs them
   * over fixtures.
   */
  record Scan(
      List<Class<?>> classes,
      Map<String, Registration> registrations,
      List<TenantIsolationCoverageBaseline.Entry> baseline,
      List<PinnedActiveTablesBaseline.Entry> pins,
      Set<String> production,
      Set<String> selfIsolated) {

    /** One check of the gate: what it requires, what to do when it fails, and what it finds. */
    record Check(String name, String guidance, Supplier<List<String>> found) {}

    /** The classes a scan of the test tree must reach for its silence to mean anything. */
    static final List<Class<?>> REACHED =
        List.of(
            TenantIsolationCoverageArchTest.class,
            CoversTenantIsolation.class,
            ProductionActiveTables.class);

    static Scan of(
        List<Class<?>> classes,
        List<TenantIsolationCoverageBaseline.Entry> baseline,
        List<PinnedActiveTablesBaseline.Entry> pins,
        Set<String> production,
        Set<String> selfIsolated) {
      return new Scan(
          classes,
          PinnedActiveTablesCatalog.dynamicRegistrations(classes),
          baseline,
          pins,
          production,
          selfIsolated);
    }

    /**
     * Every check of the gate, in the order it reports them. The gate's only test runs this list
     * over the test tree and the end-state rules test runs it over an empty baseline, so a check
     * cannot be added to the gate without being added here.
     */
    List<Check> checks(int baselineSize) {
      return List.of(
          new Check(
              "should reach this gate, the marker and the reader of the production list",
              "a scan that misses them read the wrong tree, so its silence means nothing",
              this::unreached),
          new Check(
              "should find no active table that is self-isolated",
              "A self-isolated table is never gated by the inspector, so no isolation test can"
                  + " cover it. The marker's table set needs revisiting",
              () -> List.copyOf(activeSelfIsolated())),
          new Check(
              "should find a marker that names only active tables",
              "These markers name a table that is not in the production activation list, or no"
                  + " table at all: a typo, a table that is not active yet, or a self-isolated"
                  + " table",
              this::markedButNotActive),
          new Check(
              "should find each marked table in the effective list of its class",
              "These classes carry a marker for a table their effective activation list does not"
                  + " hold, or a list the gate cannot read. Arm the table in the class, or move the"
                  + " marker to a class that does",
              this::markedOutsideTheEffectiveList),
          new Check(
              "should find no marked class that the pinned-list baseline records as drifts or"
                  + " control",
              "A class whose list differs from production for a reason that weakens it proves"
                  + " less than production does, so it does not count as an isolation test",
              this::markedButDeclaredWeaker),
          new Check(
              "should find a reasoned line for every table the baseline names",
              "every line of " + TenantIsolationCoverageBaseline.RESOURCE,
              () -> TenantIsolationCoverageBaseline.malformed(baseline)),
          new Check(
              "should find every active table covered or named in the baseline",
              "These active tables have no marked isolation test that passes the bar. Mark one"
                  + " (@CoversTenantIsolation, see its Javadoc), or, with a reason, add the table to "
                  + TenantIsolationCoverageBaseline.RESOURCE
                  + " and raise BASELINE_SIZE in review",
              () -> List.copyOf(uncovered())),
          new Check(
              "should find every baseline table active and uncovered",
              "These lines no longer describe a gap. Remove each one and lower BASELINE_SIZE: the"
                  + " baseline only shrinks",
              this::staleBaseline),
          new Check(
              "should find exactly the pinned number of baseline lines",
              "the line count of " + TenantIsolationCoverageBaseline.RESOURCE,
              () -> miscounted(baselineSize)));
    }

    List<String> unreached() {
      Set<Class<?>> seen = Set.copyOf(classes);
      return REACHED.stream().filter(type -> !seen.contains(type)).map(Class::getName).toList();
    }

    Set<String> activeSelfIsolated() {
      Set<String> both = new TreeSet<>(production);
      both.retainAll(selfIsolated);
      return both;
    }

    /** The tables the gate can ask a test to cover: the production list, self-isolated excepted. */
    Set<String> eligible() {
      Set<String> eligible = new TreeSet<>(production);
      eligible.removeAll(selfIsolated);
      return eligible;
    }

    /** The marker of each marked class, in the order of the scan. */
    Map<Class<?>, Set<String>> marked() {
      Map<Class<?>, Set<String>> marked = new LinkedHashMap<>();
      for (Class<?> type : classes) {
        CoversTenantIsolation marker = type.getAnnotation(CoversTenantIsolation.class);
        if (marker != null) {
          marked.put(type, new TreeSet<>(List.of(marker.value())));
        }
      }
      return marked;
    }

    List<String> markedButNotActive() {
      Set<String> eligible = eligible();
      List<String> found = new ArrayList<>();
      marked()
          .forEach(
              (type, tables) -> {
                if (tables.isEmpty()) {
                  found.add(type.getName() + " names no table");
                }
                tables.stream()
                    .filter(table -> !eligible.contains(table))
                    .forEach(table -> found.add(type.getName() + " names '" + table + "'"));
              });
      return found;
    }

    List<String> markedOutsideTheEffectiveList() {
      List<String> found = new ArrayList<>();
      marked()
          .forEach(
              (type, tables) -> {
                Effective effective = effective(type);
                if (effective.failure() != null) {
                  found.add(type.getName() + ": " + effective.failure());
                  return;
                }
                tables.stream()
                    .filter(table -> !effective.tables().contains(table))
                    .forEach(
                        table ->
                            found.add(
                                type.getName()
                                    + " marks '"
                                    + table
                                    + "', not in its effective list "
                                    + effective.tables()));
              });
      return found;
    }

    List<String> markedButDeclaredWeaker() {
      List<String> found = new ArrayList<>();
      weakerPins().forEach((type, lines) -> lines.forEach(line -> found.add(type + " " + line)));
      return found;
    }

    /** The marked classes whose list is a drifts or control line, with the line that says so. */
    private Map<String, List<String>> weakerPins() {
      Map<String, String> kindByClass = new TreeMap<>();
      pins.forEach(entry -> kindByClass.put(entry.testClass(), entry.kind()));
      Map<String, List<String>> weaker = new TreeMap<>();
      for (Class<?> type : marked().keySet()) {
        Set<String> names = new LinkedHashSet<>(List.of(type.getName()));
        names.addAll(effective(type).sites());
        for (String name : names) {
          String kind = kindByClass.get(name);
          if (kind != null && !"probe".equals(kind)) {
            weaker
                .computeIfAbsent(type.getName(), key -> new ArrayList<>())
                .add("runs with the list of '" + name + "', a " + kind + " line");
          }
        }
      }
      return weaker;
    }

    /**
     * The tables each class that passes the bar covers, with the classes: marked, readable list
     * holding the table, not a weaker pin. A class counts for a table only when every check above
     * holds for it.
     */
    Map<String, List<String>> covered() {
      Set<String> eligible = eligible();
      Set<String> weaker = weakerPins().keySet();
      Map<String, List<String>> covered = new TreeMap<>();
      marked()
          .forEach(
              (type, tables) -> {
                Effective effective = effective(type);
                boolean passes =
                    effective.failure() == null
                        && effective.tables().containsAll(tables)
                        && eligible.containsAll(tables)
                        && !tables.isEmpty()
                        && !weaker.contains(type.getName());
                if (passes) {
                  tables.forEach(
                      table ->
                          covered
                              .computeIfAbsent(table, key -> new ArrayList<>())
                              .add(type.getName()));
                }
              });
      return covered;
    }

    Set<String> uncovered() {
      Set<String> uncovered = new TreeSet<>(production);
      uncovered.removeAll(covered().keySet());
      baseline.forEach(entry -> uncovered.remove(entry.table()));
      return uncovered;
    }

    List<String> staleBaseline() {
      Map<String, List<String>> covered = covered();
      List<String> found = new ArrayList<>();
      for (TenantIsolationCoverageBaseline.Entry entry : baseline) {
        String where = "line " + entry.line() + " '" + entry.table() + "'";
        if (!production.contains(entry.table())) {
          found.add(where + " is not an active table");
        } else if (covered.containsKey(entry.table())) {
          found.add(where + " is covered by " + covered.get(entry.table()));
        }
      }
      return found;
    }

    List<String> miscounted(int baselineSize) {
      return baseline.size() == baselineSize
          ? List.of()
          : List.of(
              "The baseline names "
                  + baseline.size()
                  + " tables, not BASELINE_SIZE "
                  + baselineSize
                  + ". Removed one? Lower the constant. Added one? That is the review call.");
    }

    /**
     * The activation list a marked class runs with, read the way Spring resolves the key: what its
     * registration gives, else its one annotation, else the production list the CI arming sets. A
     * key set twice, or in an annotation and a registration, has no list the gate can read.
     */
    Effective effective(Class<?> type) {
      List<Declaration> declarations = PinnedActiveTablesCatalog.declarations(type);
      Map<String, Registration> mine = new TreeMap<>();
      relatedTypes(type)
          .forEach(
              related -> {
                Registration registration = registrations.get(related.getName());
                if (registration != null) {
                  mine.put(related.getName(), registration);
                }
              });
      if (mine.size() > 1) {
        return Effective.unreadable(
            "registers the key in several classes " + mine.keySet() + ", Spring keeps one");
      }
      if (!mine.isEmpty() && !declarations.isEmpty()) {
        return Effective.unreadable("sets the key in an annotation and in a registration");
      }
      if (!mine.isEmpty()) {
        Map.Entry<String, Registration> only = mine.entrySet().iterator().next();
        return only.getValue().evaluated()
            ? Effective.of(only.getValue().tables(), Set.of(only.getKey()))
            : Effective.unreadable("registration not evaluated: " + only.getValue().failure());
      }
      if (declarations.size() > 1) {
        return Effective.unreadable("sets the key in several annotations");
      }
      if (declarations.size() == 1) {
        Declaration only = declarations.get(0);
        return Effective.of(only.tables(), Set.of(only.site()));
      }
      return Effective.of(production, Set.of());
    }

    /** The class, its supertypes and the enclosing classes Spring reads configuration from. */
    private static Set<Class<?>> relatedTypes(Class<?> type) {
      Set<Class<?>> related = new LinkedHashSet<>();
      collect(type, related);
      return related;
    }

    private static void collect(Class<?> type, Set<Class<?>> related) {
      if (type == null || type == Object.class || !related.add(type)) {
        return;
      }
      for (Class<?> parent : type.getInterfaces()) {
        collect(parent, related);
      }
      collect(type.getSuperclass(), related);
      if (TestContextAnnotationUtils.searchEnclosingClass(type)) {
        collect(type.getEnclosingClass(), related);
      }
    }
  }
}
