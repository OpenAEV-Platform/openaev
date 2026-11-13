package io.openaev.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.architecture.PinnedActiveTablesCatalog.Declaration;
import io.openaev.architecture.PinnedActiveTablesCatalog.Registration;
import io.openaev.config.ProductionActiveTables;
import io.openaev.config.TenantTables;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestTemplate;
import org.junit.platform.commons.support.AnnotationSupport;
import org.junit.platform.commons.support.HierarchyTraversalMode;
import org.junit.platform.commons.support.ReflectionSupport;
import org.springframework.test.context.TestContextAnnotationUtils;

/**
 * Fails the build when a table of the production {@code openaev.tenant.active-tables} list has no
 * isolation test. A table is covered by at least one class marked {@link CoversTenantIsolation}
 * that is a runnable test, runs on the real stack with the table in its effective activation list,
 * seeds two tenants and asserts a cross-tenant refusal on the table itself. The gate checks the
 * marker, the arming and the runnable test; the two tenants and the refusal rest on the reading of
 * whoever adds the marker (see the marker's Javadoc).
 *
 * <p>The effective list of a marked class is the one it declares, read with the catalog of {@link
 * PinnedActiveTablesArchTest}: the one annotation that sets the key ({@link
 * PinnedActiveTablesCatalog#declarations}), or what its {@code DynamicPropertySource} methods
 * register ({@link PinnedActiveTablesCatalog#dynamicRegistrations}), on the class, its supertypes
 * and its enclosing classes. A class that declares nothing covers nothing: the default run of the
 * gating CI declares no active tables, and the shadow runs that arm the production list report
 * without failing the workflow, so only a declaration of the class itself proves the table is armed
 * when the class runs. A class that sets the key twice, both ways, in a registration the scan
 * cannot evaluate, or with {@code *} has no list the gate can read and covers nothing. The shapes
 * the catalog cannot see (property files, profiles, {@code DynamicPropertyRegistrar}) are listed in
 * the "Not seen" paragraph of {@link PinnedActiveTablesCatalog}.
 *
 * <p>A runnable test is a class that is not abstract and not {@link Disabled} (nor a {@link Nested}
 * class of a disabled class), with a method Jupiter runs as a test (not private, not static) that
 * is not itself disabled, in the class, its supertypes or a nested class that is not disabled. A
 * disabled parent class does not disable its subclass, as in JUnit. {@code EnabledIf}, {@code
 * DisabledIf} and the other conditions are not read.
 *
 * <p>A class whose pin the baseline of {@link PinnedActiveTablesBaseline} records as {@code drifts}
 * or {@code control} covers nothing: its list is known to differ from production for a reason that
 * makes its assertions weaker than production's.
 *
 * <p>The tables that no class covers yet are named, with what is missing and why, in {@code
 * tenant-isolation-coverage-baseline.txt}. A line whose table becomes covered or inactive fails,
 * and its size is frozen by {@link #BASELINE_SIZE}.
 */
@DisplayName("Every active table has a class marked as its isolation test")
class TenantIsolationCoverageArchTest {

  /**
   * How many tables the baseline names. Lower it when a line goes. Raise it only in a change that
   * removes the marker of a class found not to assert the refusal on the table itself, and say why
   * in that change: a new activation never adds a line.
   */
  static final int BASELINE_SIZE = 15;

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
   * {@code sites} are the classes that decide the list, the ones the pinned-list baseline may name.
   * {@code tables} is null exactly when {@code failure} is set.
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
   * The test above runs the checks over the test tree; {@link TenantIsolationCoverageRulesTest}
   * runs them over fixtures.
   */
  record Scan(
      List<Class<?>> classes,
      Map<String, Registration> registrations,
      List<TenantIsolationCoverageBaseline.Entry> baseline,
      List<PinnedActiveTablesBaseline.Entry> pins,
      Set<String> production,
      Set<String> selfIsolated) {

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
              "should find each marked class runnable, with its tables in the effective list",
              "These classes are not runnable tests, declare no activation list, carry a marker for"
                  + " a table their effective list does not hold, or have a list the gate cannot"
                  + " read. A class with no declaration is not armed in the gating CI run: register"
                  + " ProductionActiveTables.names() in a @DynamicPropertySource, or pin the table."
                  + " Otherwise move the marker to a class that is armed",
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
              "These active tables have no marked isolation test that passes the bar. Mark a class"
                  + " that meets it (@CoversTenantIsolation, see its Javadoc). The baseline never"
                  + " grows, so "
                  + TenantIsolationCoverageBaseline.RESOURCE
                  + " takes no new line",
              () -> List.copyOf(uncovered())),
          new Check(
              "should find every baseline table active and uncovered",
              "These lines no longer describe a gap. Remove each one and lower BASELINE_SIZE: the"
                  + " baseline never grows",
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

    /** Why a marked class is not a runnable test, by class; a runnable class is absent. */
    Map<Class<?>, String> unrunnable() {
      Map<Class<?>, String> unrunnable = new LinkedHashMap<>();
      for (Class<?> type : marked().keySet()) {
        if (Modifier.isAbstract(type.getModifiers())) {
          unrunnable.put(type, "is abstract");
        } else if (isDisabled(type)) {
          unrunnable.put(type, "is @Disabled, or nested in a disabled class");
        } else if (!hasTestMethod(type)) {
          unrunnable.put(type, "has no enabled test method");
        }
      }
      return unrunnable;
    }

    /**
     * {@link Disabled} on the class or as a meta-annotation, or on the enclosing class while the
     * class is {@link Nested}. The annotation is not inherited, so a disabled parent does not
     * disable its subclass, as in JUnit.
     */
    private static boolean isDisabled(Class<?> type) {
      for (Class<?> c = type; c != null; c = c.getEnclosingClass()) {
        if (AnnotationSupport.isAnnotated(c, Disabled.class)) {
          return true;
        }
        if (!AnnotationSupport.isAnnotated(c, Nested.class)) {
          return false;
        }
      }
      return false;
    }

    /**
     * An enabled method Jupiter runs as a test, in the class, its supertypes or a nested class not
     * disabled. Jupiter discovers a method carrying {@code @Test}, {@code @TestFactory} or
     * {@code @TestTemplate} (which {@code @ParameterizedTest} and {@code @RepeatedTest} carry),
     * directly or as a meta-annotation, and skips one that is private or static. {@code @Testable}
     * is the Platform's marker and runs nothing by itself.
     */
    private static boolean hasTestMethod(Class<?> type) {
      boolean own =
          !ReflectionSupport.findMethods(
                  type,
                  method ->
                      isJupiterTestMethod(method)
                          && !AnnotationSupport.isAnnotated(method, Disabled.class),
                  HierarchyTraversalMode.TOP_DOWN)
              .isEmpty();
      return own
          || ReflectionSupport.findNestedClasses(
                  type, nested -> AnnotationSupport.isAnnotated(nested, Nested.class))
              .stream()
              .anyMatch(nested -> !isDisabled(nested) && hasTestMethod(nested));
    }

    private static boolean isJupiterTestMethod(Method method) {
      return !Modifier.isPrivate(method.getModifiers())
          && !Modifier.isStatic(method.getModifiers())
          && (AnnotationSupport.isAnnotated(method, Test.class)
              || AnnotationSupport.isAnnotated(method, TestFactory.class)
              || AnnotationSupport.isAnnotated(method, TestTemplate.class));
    }

    List<String> markedOutsideTheEffectiveList() {
      List<String> found = new ArrayList<>();
      unrunnable().forEach((type, why) -> found.add(type.getName() + " " + why));
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
          if (kind != null
              && (PinnedActiveTablesBaseline.DEBT_KINDS.contains(kind)
                  || PinnedActiveTablesBaseline.EXPIRING_KIND.equals(kind))) {
            weaker
                .computeIfAbsent(type.getName(), key -> new ArrayList<>())
                .add("runs with the list of '" + name + "', a " + kind + " line");
          }
        }
      }
      return weaker;
    }

    /**
     * The classes that cover each table: marked, runnable, with a readable declared list holding
     * the table, and not a weaker pin. A class counts for a table only when every check above holds
     * for it.
     */
    Map<String, List<String>> covered() {
      Set<String> eligible = eligible();
      Set<String> weaker = weakerPins().keySet();
      Set<Class<?>> unrunnable = unrunnable().keySet();
      Map<String, List<String>> covered = new TreeMap<>();
      marked()
          .forEach(
              (type, tables) -> {
                Effective effective = effective(type);
                boolean passes =
                    !unrunnable.contains(type)
                        && effective.failure() == null
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
      Set<String> uncovered = eligible();
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
                  + ". Removed one? Lower the constant. The baseline never grows.");
    }

    /**
     * The activation list a marked class declares, read the way Spring resolves the key: what its
     * registration gives, else its one annotation. A class that declares nothing, sets the key
     * twice, or in an annotation and a registration, or lists {@code *}, has no list the gate can
     * read.
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
            "registers the key in several classes " + mine.keySet() + ", Spring resolves one");
      }
      if (!mine.isEmpty() && !declarations.isEmpty()) {
        return Effective.unreadable("sets the key in an annotation and in a registration");
      }
      if (!mine.isEmpty()) {
        Map.Entry<String, Registration> only = mine.entrySet().iterator().next();
        return only.getValue().evaluated()
            ? declared(only.getValue().tables(), only.getKey())
            : Effective.unreadable("registration not evaluated: " + only.getValue().failure());
      }
      if (declarations.size() > 1) {
        return Effective.unreadable("sets the key in several annotations");
      }
      if (declarations.size() == 1) {
        Declaration only = declarations.get(0);
        return declared(only.tables(), only.site());
      }
      return Effective.unreadable(
          "declares no activation list, so the gating CI run arms no table for it; register"
              + " ProductionActiveTables.names() in a @DynamicPropertySource");
    }

    private static Effective declared(Set<String> tables, String site) {
      return tables.contains("*")
          ? Effective.unreadable("lists '*' in " + site + ", a wildcard the gate does not expand")
          : Effective.of(tables, Set.of(site));
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
