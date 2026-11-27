package io.openaev.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

/**
 * Fails the build when a gated tenant table has no isolation test. A table is covered by at least
 * one class marked {@link CoversTenantIsolation} that is a runnable test and names only gated
 * tables, runs on the real stack, seeds two tenants and asserts a cross-tenant refusal on the table
 * itself. The gate checks the marker and the runnable test; the real stack, the two tenants and the
 * refusal rest on the reading of whoever adds the marker (see the marker's Javadoc). Every test
 * runs with every tenant table gated, so the gate has no per-class list to read.
 *
 * <p>A runnable test is a class that is not abstract and not {@link Disabled} (nor a {@link Nested}
 * class of a disabled class), with a method Jupiter runs as a test (not private, not static) that
 * is not itself disabled, in the class, its supertypes or a nested class that is not disabled. A
 * disabled parent class does not disable its subclass, as in JUnit. {@code EnabledIf}, {@code
 * DisabledIf} and the other conditions are not read.
 *
 * <p>The tables that no class covers yet are named, with what is missing and why, in {@code
 * tenant-isolation-coverage-baseline.txt}. A line whose table becomes covered or stops being gated
 * fails, and its size is frozen by {@link #BASELINE_SIZE}.
 */
@DisplayName("Every gated table has a class marked as its isolation test")
class TenantIsolationCoverageArchTest {

  /**
   * How many tables the baseline names. Lower it when a line goes. Raise it only in a change that
   * removes the marker of a class found not to assert the refusal on the table itself, and say why
   * in that change: a new tenant table never adds a line.
   */
  static final int BASELINE_SIZE = 1;

  /**
   * The package of the fixtures of {@link TenantIsolationCoverageRulesTest}, left out of the tree.
   */
  static final String FIXTURE_PACKAGE = "io.openaev.architecture.fixture";

  @TestFactory
  @DisplayName("given the test tree, should pass every check of the gate")
  Stream<DynamicTest> given_testTree_should_passEveryCheckOfTheGate() {
    Scan scan =
        new Scan(
            testTreeClasses(),
            TenantIsolationCoverageBaseline.load(),
            BackgroundEntrypointTenantScopeArchTest.gatedTenantTables());
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

  /** Every class of the test tree but the fixtures, anonymous and local classes excepted. */
  static List<Class<?>> testTreeClasses() {
    return testClasses(
        "io.openaev", javaClass -> !FIXTURE_PACKAGE.equals(javaClass.getPackageName()));
  }

  /** The fixtures, imported the way the test tree is. */
  static List<Class<?>> fixtureClasses() {
    return testClasses(FIXTURE_PACKAGE, javaClass -> true);
  }

  private static List<Class<?>> testClasses(
      String packageName, java.util.function.Predicate<JavaClass> keep) {
    ClassLoader loader = TenantIsolationCoverageArchTest.class.getClassLoader();
    return new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
            .importPackages(packageName)
            .stream()
            .filter(keep)
            .filter(javaClass -> !javaClass.isAnonymousClass() && !javaClass.isLocalClass())
            .map(JavaClass::getName)
            .sorted()
            .<Class<?>>map(name -> load(name, loader))
            .toList();
  }

  private static Class<?> load(String name, ClassLoader loader) {
    try {
      return Class.forName(name, false, loader);
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException("cannot load test class " + name, e);
    }
  }

  /**
   * The test above runs the checks over the test tree; {@link TenantIsolationCoverageRulesTest}
   * runs them over fixtures.
   */
  record Scan(
      List<Class<?>> classes,
      List<TenantIsolationCoverageBaseline.Entry> baseline,
      Set<String> gated) {

    record Check(String name, String guidance, Supplier<List<String>> found) {}

    /** The classes a scan of the test tree must reach for its silence to mean anything. */
    static final List<Class<?>> REACHED =
        List.of(TenantIsolationCoverageArchTest.class, CoversTenantIsolation.class);

    /**
     * Every check of the gate, in the order it reports them. The gate's only test runs this list
     * over the test tree and the end-state rules test runs it over an empty baseline, so a check
     * cannot be added to the gate without being added here.
     */
    List<Check> checks(int baselineSize) {
      return List.of(
          new Check(
              "should reach this gate and the marker",
              "a scan that misses them read the wrong tree, so its silence means nothing",
              this::unreached),
          new Check(
              "should find a marker that names only gated tables",
              "These markers name a table the statement inspector does not gate, or no table at"
                  + " all: a typo, a table that is not a tenant table, or a self-isolated table",
              this::markedButNotGated),
          new Check(
              "should find each marked class runnable",
              "These classes are not runnable tests: move the marker to a class Jupiter runs",
              this::markedButNotRunnable),
          new Check(
              "should find a reasoned line for every table the baseline names",
              "every line of " + TenantIsolationCoverageBaseline.RESOURCE,
              () -> TenantIsolationCoverageBaseline.malformed(baseline)),
          new Check(
              "should find every gated table covered or named in the baseline",
              "These gated tables have no marked isolation test that passes the bar. Mark a class"
                  + " that meets it (@CoversTenantIsolation, see its Javadoc). The baseline never"
                  + " grows, so "
                  + TenantIsolationCoverageBaseline.RESOURCE
                  + " takes no new line",
              () -> List.copyOf(uncovered())),
          new Check(
              "should find every baseline table gated and uncovered",
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

    List<String> markedButNotGated() {
      List<String> found = new ArrayList<>();
      marked()
          .forEach(
              (type, tables) -> {
                if (tables.isEmpty()) {
                  found.add(type.getName() + " names no table");
                }
                tables.stream()
                    .filter(table -> !gated.contains(table))
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

    List<String> markedButNotRunnable() {
      List<String> found = new ArrayList<>();
      unrunnable().forEach((type, why) -> found.add(type.getName() + " " + why));
      return found;
    }

    /**
     * The classes that cover each table: marked, runnable, naming only gated tables. A class counts
     * for a table only when every check above holds for it.
     */
    Map<String, List<String>> covered() {
      Set<Class<?>> unrunnable = unrunnable().keySet();
      Map<String, List<String>> covered = new TreeMap<>();
      marked()
          .forEach(
              (type, tables) -> {
                boolean passes =
                    !unrunnable.contains(type) && !tables.isEmpty() && gated.containsAll(tables);
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
      Set<String> uncovered = new TreeSet<>(gated);
      uncovered.removeAll(covered().keySet());
      baseline.forEach(entry -> uncovered.remove(entry.table()));
      return uncovered;
    }

    List<String> staleBaseline() {
      Map<String, List<String>> covered = covered();
      List<String> found = new ArrayList<>();
      for (TenantIsolationCoverageBaseline.Entry entry : baseline) {
        String where = "line " + entry.line() + " '" + entry.table() + "'";
        if (!gated.contains(entry.table())) {
          found.add(where + " is not a gated table");
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
  }
}
