package io.openaev.architecture;

import static java.util.stream.Collectors.toCollection;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMember;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchIgnore;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;
import java.util.Map;
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
 * An {@link ArchRule} field without {@link ArchTest} compiles, reads like a guard, and never runs:
 * the ArchUnit engine only executes annotated members, so nothing goes red when the rule is broken.
 * Two tenant-active-table rules lived that way, one of them since another rule was inserted between
 * it and its annotation. This test makes every {@link ArchRule} member of the architecture tests
 * either an {@link ArchTest} in a class the ArchUnit engine runs, or a helper listed in {@link
 * #HELPERS} with the runner that actually reads it.
 *
 * <p>Denominator: the test classes of {@code io.openaev.architecture}, its subpackages included,
 * which today hold every {@link ArchRule} of the test tree. A rule declared in another package is
 * not seen.
 */
@DisplayName("Every architecture rule runs: an @ArchTest, or a listed helper read by its runner")
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
   * must read it, so a helper whose runner stops reading it goes red here instead of going quiet.
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

  private static Stream<JavaMember> archRuleMembers() {
    return ARCHITECTURE_TESTS.stream()
        .flatMap(
            javaClass ->
                Stream.concat(
                    javaClass.getFields().stream()
                        .filter(field -> field.getRawType().isAssignableTo(ArchRule.class)),
                    javaClass.getMethods().stream()
                        .filter(method -> method.getRawReturnType().isAssignableTo(ArchRule.class))
                        .filter(method -> !method.getModifiers().contains(JavaModifier.SYNTHETIC))))
        .map(JavaMember.class::cast);
  }

  private static String key(JavaMember member) {
    return member.getOwner().getName() + "#" + member.getName();
  }

  private static Set<String> unannotatedMembers() {
    return archRuleMembers()
        .filter(member -> !member.isAnnotatedWith(ArchTest.class))
        .map(ArchRuleRegistrationTest::key)
        .collect(toCollection(TreeSet::new));
  }

  private static Set<String> helperKeys() {
    return HELPERS.stream().map(Helper::key).collect(toCollection(TreeSet::new));
  }

  @Nested
  @DisplayName("Rule members")
  class RuleMembers {

    @Test
    @DisplayName("given the architecture tests, should see their rules")
    void given_theArchitectureTests_should_seeTheirRules() {
      // Act
      Set<String> annotated =
          archRuleMembers()
              .filter(member -> member.isAnnotatedWith(ArchTest.class))
              .map(ArchRuleRegistrationTest::key)
              .collect(toCollection(TreeSet::new));

      // Assert: an import that sees nothing would pass every check below
      assertThat(annotated)
          .contains(
              TenantActiveTableAccessArchTest.class.getName()
                  + "#import_mappers_repository_access_is_reviewed");
    }

    @Test
    @DisplayName("given an ArchRule member, should be an @ArchTest or a listed helper")
    void given_anArchRuleMember_should_beAnArchTestOrAListedHelper() {
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
          archRuleMembers()
              .filter(member -> member.isAnnotatedWith(ArchTest.class))
              .map(JavaMember::getOwner)
              .filter(
                  owner ->
                      !owner.isAnnotatedWith(AnalyzeClasses.class)
                          || !SUREFIRE_DEFAULT_INCLUDES.matcher(owner.getSimpleName()).matches())
              .map(JavaClass::getName)
              .collect(toCollection(TreeSet::new));

      // Assert
      assertThat(unrun)
          .as(
              "an @ArchTest runs only in a class carrying @AnalyzeClasses whose name surefire"
                  + " includes")
          .isEmpty();
    }

    @Test
    @DisplayName("given an @ArchTest member, should not be switched off")
    void given_anArchTestMember_should_notBeSwitchedOff() {
      // Act
      Set<String> switchedOff =
          archRuleMembers()
              .filter(member -> member.isAnnotatedWith(ArchTest.class))
              .filter(
                  member ->
                      member.isAnnotatedWith(ArchIgnore.class)
                          || member.getOwner().isAnnotatedWith(ArchIgnore.class)
                          || member.getOwner().isAnnotatedWith(Disabled.class))
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
          .as("these HELPERS lines name no unannotated ArchRule member any more: remove them")
          .isEmpty();
    }

    @Test
    @DisplayName("given a helper, should be read by its runner")
    void given_aHelper_should_beReadByItsRunner() {
      // Act
      Map<String, String> unread = new TreeMap<>();
      for (Helper helper : HELPERS) {
        boolean read =
            ARCHITECTURE_TESTS.stream()
                .filter(
                    javaClass ->
                        javaClass.getName().equals(helper.runner().getName())
                            || javaClass.getName().startsWith(helper.runner().getName() + "$"))
                .flatMap(javaClass -> javaClass.getAccessesFromSelf().stream())
                .anyMatch(
                    access ->
                        access.getTargetOwner().isEquivalentTo(helper.holder())
                            && access.getTarget().getName().equals(helper.member()));
        if (!read) {
          unread.put(helper.key(), helper.runner().getName());
        }
      }

      // Assert
      assertThat(unread)
          .as("these helpers are not read by the runner HELPERS names, so nothing runs them")
          .isEmpty();
    }
  }
}
