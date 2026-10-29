package io.openaev.architecture;

import static java.util.stream.Collectors.toCollection;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaMember;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaStaticInitializer;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchIgnore;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * An architecture rule without {@link ArchTest} compiles, reads like a guard, and never runs: the
 * ArchUnit engine only executes annotated members, so nothing goes red when the rule is broken. Two
 * tenant-active-table rules lived that way, one of them since another rule was inserted between it
 * and its annotation. This test checks where each rule of the architecture tests is registered:
 *
 * <ul>
 *   <li>a rule member, meaning an {@link ArchRule} field, a method returning one, or a {@code void}
 *       method taking only {@link JavaClasses}, is an {@link ArchTest} or a helper listed in {@link
 *       #HELPERS};
 *   <li>every {@link ArchTest} member, of any type, sits in a top-level {@link AnalyzeClasses}
 *       class whose name surefire includes, and neither it nor its class is under {@link
 *       ArchIgnore} or {@link Disabled};
 *   <li>every helper is read by the runner its line names, from a place the build runs: the static
 *       initializer of a class the ArchUnit engine runs and that is not switched off, or a {@link
 *       Test} method that is not {@link Disabled}, in classes that are not {@link Disabled}, whose
 *       inner classes carry {@link Nested} and whose top-level class has a name surefire includes.
 * </ul>
 *
 * <p>Denominator: the test classes of {@code io.openaev.architecture}, its subpackages included,
 * which today hold every {@link ArchRule} of the test tree. A rule declared in another package is
 * not seen. A helper read only from a lambda counts as not run, so that shape fails closed. It does
 * not check what the runner does with the helper it reads: a read whose rule is never passed to
 * {@code check} still counts as run.
 */
@DisplayName(
    "Every architecture rule is an @ArchTest the build runs, or a listed helper read where it runs")
class ArchRuleRegistrationTest {

  private static final JavaClasses ARCHITECTURE_TESTS =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
          .importPackages(ArchRuleRegistrationTest.class.getPackageName());

  /** Surefire's default includes: a class outside them is never executed by the build. */
  private static final Pattern SUREFIRE_DEFAULT_INCLUDES =
      Pattern.compile("Test.*|.*Test|.*Tests|.*TestCase");

  private record Helper(Class<?> holder, String member, Class<?> runner, String reason) {

    String key() {
      return holder.getName() + "#" + member;
    }
  }

  /**
   * Rules built outside the class that runs them. Each one is executed by {@code runner}, which
   * must read it where the build runs it, so a helper whose runner stops reading it, or stops being
   * run, goes red here instead of going quiet.
   */
  private static final List<Helper> HELPERS =
      List.of(
          new Helper(
              HandledErrorRollbackRules.class,
              "HANDLED_ERRORS_THAT_DIE_AT_COMMIT_ARE_REVIEWED",
              HandledErrorRollbackArchTest.class,
              "shared with HandledErrorRollbackRulesTest, which proves it fires on fixtures"),
          new Helper(
              TenantBackgroundTransactionRules.class,
              "NO_TRANSACTIONAL_SELF_INVOCATION",
              TenantBackgroundTransactionArchTest.class,
              "the runner wraps it in a frozen rule; the raw rule is what the fixtures test"),
          new Helper(
              TenantBackgroundTransactionRules.class,
              "NO_TRANSACTIONAL_IN_JOBS",
              TenantBackgroundTransactionArchTest.class,
              "the runner wraps it in a frozen rule; the raw rule is what the fixtures test"),
          new Helper(
              TenantBackgroundTransactionRules.class,
              "NO_RAW_TRANSACTION_PLUMBING_IN_JOBS",
              TenantBackgroundTransactionArchTest.class,
              "the runner wraps it in a frozen rule; the raw rule is what the fixtures test"),
          new Helper(
              TenantBackgroundTransactionRules.class,
              "NO_PRIMITIVE_ON_HTTP_PATH",
              TenantBackgroundTransactionArchTest.class,
              "the runner wraps it in a frozen rule; the raw rule is what the fixtures test"),
          new Helper(
              TenantBackgroundTransactionRules.class,
              "noTenantFilterDisableOutsideBaseline",
              TenantFilterDisableArchTest.class,
              "parameterised by the baseline, so the runner checks it from a @Test"));

  /** The members the ArchUnit engine can run as a rule, whether or not they are annotated. */
  private static Stream<JavaMember> ruleMembers() {
    return ARCHITECTURE_TESTS.stream()
        .flatMap(
            javaClass ->
                Stream.<JavaMember>concat(
                    javaClass.getFields().stream()
                        .filter(field -> field.getRawType().isAssignableTo(ArchRule.class)),
                    javaClass.getMethods().stream()
                        .filter(
                            method ->
                                method.getRawReturnType().isAssignableTo(ArchRule.class)
                                    || isVoidRuleMethod(method))
                        .filter(
                            method -> !method.getModifiers().contains(JavaModifier.SYNTHETIC))));
  }

  private static boolean isVoidRuleMethod(JavaMethod method) {
    List<JavaClass> parameters = method.getRawParameterTypes();
    return method.getRawReturnType().isEquivalentTo(void.class)
        && parameters.size() == 1
        && parameters.get(0).isEquivalentTo(JavaClasses.class);
  }

  /** Every member annotated {@link ArchTest}, whatever its type. */
  private static Stream<JavaMember> archTestMembers() {
    return ARCHITECTURE_TESTS.stream()
        .flatMap(
            javaClass ->
                Stream.<JavaMember>concat(
                    javaClass.getFields().stream(), javaClass.getMethods().stream()))
        .filter(member -> member.isAnnotatedWith(ArchTest.class));
  }

  private static String key(JavaMember member) {
    return member.getOwner().getName() + "#" + member.getName();
  }

  private static Set<String> unannotatedMembers() {
    return ruleMembers()
        .filter(member -> !member.isAnnotatedWith(ArchTest.class))
        .map(ArchRuleRegistrationTest::key)
        .collect(toCollection(TreeSet::new));
  }

  /** The class itself, then each class enclosing it, up to the top-level one. */
  private static Stream<JavaClass> selfAndEnclosing(JavaClass javaClass) {
    return Stream.iterate(
            Optional.of(javaClass),
            Optional::isPresent,
            current -> current.get().getEnclosingClass())
        .map(Optional::get);
  }

  private static boolean switchedOff(JavaClass javaClass) {
    return selfAndEnclosing(javaClass)
        .anyMatch(
            current ->
                current.isAnnotatedWith(ArchIgnore.class)
                    || current.isAnnotatedWith(Disabled.class));
  }

  private static boolean runByTheArchUnitEngine(JavaClass javaClass) {
    return javaClass.isTopLevelClass()
        && javaClass.isAnnotatedWith(AnalyzeClasses.class)
        && SUREFIRE_DEFAULT_INCLUDES.matcher(javaClass.getSimpleName()).matches();
  }

  /** Why the build never executes this read of a helper, or empty when it does. */
  private static Optional<String> whyNotRun(JavaCodeUnit reader) {
    JavaClass owner = reader.getOwner();
    String where = owner.getName() + "#" + reader.getName();
    if (reader instanceof JavaStaticInitializer) {
      if (!runByTheArchUnitEngine(owner)) {
        return Optional.of(where + " initializes a class the ArchUnit engine does not run");
      }
      if (switchedOff(owner)) {
        return Optional.of(where + " initializes a class under @ArchIgnore or @Disabled");
      }
      return Optional.empty();
    }
    if (!reader.isAnnotatedWith(Test.class)) {
      return Optional.of(where + " carries no @Test");
    }
    if (reader.isAnnotatedWith(Disabled.class) || switchedOff(owner)) {
      return Optional.of(where + " is under @Disabled, on itself or on an enclosing class");
    }
    if (selfAndEnclosing(owner)
        .anyMatch(
            current -> !current.isTopLevelClass() && !current.isAnnotatedWith(Nested.class))) {
      return Optional.of(where + " sits in an inner class without @Nested");
    }
    JavaClass topLevel = selfAndEnclosing(owner).reduce((inner, outer) -> outer).orElseThrow();
    if (!SUREFIRE_DEFAULT_INCLUDES.matcher(topLevel.getSimpleName()).matches()) {
      return Optional.of(where + " sits in " + topLevel.getName() + ", a name surefire excludes");
    }
    return Optional.empty();
  }

  /** The code units of the helper's runner, its nested classes included, that read the helper. */
  private static List<JavaCodeUnit> readersOf(Helper helper) {
    return ARCHITECTURE_TESTS.stream()
        .filter(
            javaClass ->
                javaClass.getName().equals(helper.runner().getName())
                    || javaClass.getName().startsWith(helper.runner().getName() + "$"))
        .flatMap(javaClass -> javaClass.getAccessesFromSelf().stream())
        .filter(
            access ->
                access.getTargetOwner().isEquivalentTo(helper.holder())
                    && access.getTarget().getName().equals(helper.member()))
        .map(JavaAccess::getOrigin)
        .distinct()
        .toList();
  }

  private static Set<String> helperKeys() {
    return HELPERS.stream().map(Helper::key).collect(toCollection(TreeSet::new));
  }

  @Nested
  @DisplayName("Rule members")
  class RuleMembers {

    @Test
    @DisplayName("given the architecture tests, should see their rules of every shape")
    void given_theArchitectureTests_should_seeTheirRulesOfEveryShape() {
      // Act
      Set<String> annotated =
          ruleMembers()
              .filter(member -> member.isAnnotatedWith(ArchTest.class))
              .map(ArchRuleRegistrationTest::key)
              .collect(toCollection(TreeSet::new));

      // Assert: an import that sees nothing, or no void rule, would pass every check below
      assertThat(annotated)
          .contains(
              TenantActiveTableAccessArchTest.class.getName()
                  + "#import_mappers_repository_access_is_reviewed",
              TenantActiveTableAccessArchTest.class.getName() + "#every_active_table_is_guarded");
    }

    @Test
    @DisplayName("given a rule member, should be an @ArchTest or a listed helper")
    void given_aRuleMember_should_beAnArchTestOrAListedHelper() {
      // Act
      Set<String> neverRun = unannotatedMembers();
      neverRun.removeAll(helperKeys());

      // Assert
      assertThat(neverRun)
          .as(
              "these rules are not annotated @ArchTest, so the ArchUnit engine never runs them:"
                  + " annotate them, or list them in HELPERS with the class that runs them")
          .isEmpty();
    }

    @Test
    @DisplayName("given an @ArchTest member, should sit in a class the build runs")
    void given_anArchTestMember_should_sitInAClassTheBuildRuns() {
      // Act
      Set<String> unrun =
          archTestMembers()
              .filter(member -> !runByTheArchUnitEngine(member.getOwner()))
              .map(ArchRuleRegistrationTest::key)
              .collect(toCollection(TreeSet::new));

      // Assert
      assertThat(unrun)
          .as(
              "an @ArchTest runs only in a top-level class carrying @AnalyzeClasses whose name"
                  + " surefire includes")
          .isEmpty();
    }

    @Test
    @DisplayName("given an @ArchTest member, should not be switched off")
    void given_anArchTestMember_should_notBeSwitchedOff() {
      // Act
      Set<String> switchedOff =
          archTestMembers()
              .filter(
                  member ->
                      member.isAnnotatedWith(ArchIgnore.class)
                          || member.isAnnotatedWith(Disabled.class)
                          || switchedOff(member.getOwner()))
              .map(ArchRuleRegistrationTest::key)
              .collect(toCollection(TreeSet::new));

      // Assert
      assertThat(switchedOff)
          .as("@ArchIgnore or @Disabled skips these rules: delete the rule or let it run")
          .isEmpty();
    }
  }

  @Nested
  @DisplayName("Helpers")
  class Helpers {

    @Test
    @DisplayName("given the helper list, should name only unannotated rule members")
    void given_theHelperList_should_nameOnlyUnannotatedRuleMembers() {
      // Act
      Set<String> stale = helperKeys();
      stale.removeAll(unannotatedMembers());

      // Assert
      assertThat(stale)
          .as("these HELPERS lines name no unannotated rule member any more: remove them")
          .isEmpty();
    }

    @Test
    @DisplayName("given a helper, should be read by its runner where the build runs it")
    void given_aHelper_should_beReadByItsRunnerWhereTheBuildRunsIt() {
      // Act
      Map<String, List<String>> notRun = new TreeMap<>();
      for (Helper helper : HELPERS) {
        List<JavaCodeUnit> readers = readersOf(helper);
        List<String> reasons =
            readers.stream()
                .map(ArchRuleRegistrationTest::whyNotRun)
                .flatMap(Optional::stream)
                .toList();
        if (readers.isEmpty()) {
          notRun.put(helper.key(), List.of("never read by " + helper.runner().getName()));
        } else if (reasons.size() == readers.size()) {
          notRun.put(helper.key(), reasons);
        }
      }

      // Assert
      assertThat(notRun)
          .as(
              "these helpers are not read by the runner HELPERS names from a place the build"
                  + " runs, so nothing runs them")
          .isEmpty();
    }
  }
}
