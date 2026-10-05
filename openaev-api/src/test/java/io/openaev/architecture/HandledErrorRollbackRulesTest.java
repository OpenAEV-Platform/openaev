package io.openaev.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.EvaluationResult;
import io.openaev.architecture.fixture.HandledErrorRollbackFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Proves the #8102 guard fires on the defect and stays quiet on the shapes that are safe for a
 * reason. {@link HandledErrorRollbackArchTest} runs the same rule over production classes, where
 * the reviewed allowlist keeps it green; here it runs over fixtures, so a pass can only come from a
 * REAL violation and never from ArchUnit's empty-should error.
 */
@DisplayName("Handled errors that die at commit: the guard fires, and only where it should")
class HandledErrorRollbackRulesTest {

  private static EvaluationResult evaluate(Class<?>... classes) {
    JavaClasses fixture = new ClassFileImporter().importClasses(classes);
    return HandledErrorRollbackRules.HANDLED_ERRORS_THAT_DIE_AT_COMMIT_ARE_REVIEWED.evaluate(
        fixture);
  }

  @Nested
  @DisplayName("the shapes that die at commit")
  class DefectiveShapes {

    @Test
    @DisplayName(
        "given a transactional caller catching another bean's transactional failure, should"
            + " violate")
    void given_a_transactional_caller_catching_a_boundary_failure_should_violate() {
      // WHEN
      EvaluationResult result =
          evaluate(
              HandledErrorRollbackFixtures.CatchingCaller.class,
              HandledErrorRollbackFixtures.CheckedBoundary.class,
              HandledErrorRollbackFixtures.BoundaryFailure.class);

      // THEN
      assertTrue(result.hasViolation(), "the rule must flag the catching caller");
      assertTrue(
          result.getFailureReport().toString().contains("CatchingCaller#handle"),
          "the violation must name the catching method");
    }

    @Test
    @DisplayName(
        "given the boundary is reached through a non-transactional bean, should still violate")
    void given_the_boundary_is_reached_through_a_pass_through_should_still_violate() {
      // GIVEN the #8102 shape: StixApi catches around StixService, which carries no @Transactional
      // at all and forwards to SecurityCoverageService, which does.

      // WHEN
      EvaluationResult result =
          evaluate(
              HandledErrorRollbackFixtures.CatchingCallerThroughPassThrough.class,
              HandledErrorRollbackFixtures.PassThrough.class,
              HandledErrorRollbackFixtures.CheckedBoundary.class,
              HandledErrorRollbackFixtures.BoundaryFailure.class);

      // THEN
      assertTrue(
          result.hasViolation(),
          "a rule that only looked at the call inside the try block would miss #8102 itself");
      assertTrue(
          result
              .getFailureReport()
              .toString()
              .contains("CatchingCallerThroughPassThrough#handle -> CheckedBoundary.write"),
          "the violation must name the boundary that marks the transaction, not the pass-through");
    }
  }

  @Nested
  @DisplayName("the shapes that are safe for a reason")
  class SafeShapes {

    @Test
    @DisplayName("given no transaction is open at the catch, should not violate")
    void given_no_transaction_is_open_at_the_catch_should_not_violate() {
      // WHEN
      EvaluationResult result =
          evaluate(
              HandledErrorRollbackFixtures.UntransactionalCaller.class,
              HandledErrorRollbackFixtures.CheckedBoundary.class,
              HandledErrorRollbackFixtures.BoundaryFailure.class);

      // THEN
      assertFalse(
          result.hasViolation(),
          "with no outer transaction the boundary owns the rollback and rethrows the original");
    }

    @Test
    @DisplayName("given the boundary runs REQUIRES_NEW, should not violate")
    void given_the_boundary_runs_in_its_own_transaction_should_not_violate() {
      // WHEN
      EvaluationResult result =
          evaluate(
              HandledErrorRollbackFixtures.IsolatedBoundaryCaller.class,
              HandledErrorRollbackFixtures.CheckedBoundary.class,
              HandledErrorRollbackFixtures.BoundaryFailure.class);

      // THEN
      assertFalse(
          result.hasViolation(), "a REQUIRES_NEW failure rolls back only its own transaction");
    }

    @Test
    @DisplayName("given the boundary declares noRollbackFor for what is caught, should not violate")
    void given_the_boundary_declares_no_rollback_for_what_is_caught_should_not_violate() {
      // WHEN
      EvaluationResult result =
          evaluate(
              HandledErrorRollbackFixtures.HandledFailureCaller.class,
              HandledErrorRollbackFixtures.CheckedBoundary.class,
              HandledErrorRollbackFixtures.BoundaryFailure.class);

      // THEN
      assertFalse(result.hasViolation(), "noRollbackFor on the inner boundary is the fix");
    }

    @Test
    @DisplayName("given a checked exception under default rollback rules, should not violate")
    void given_a_checked_exception_under_default_rollback_rules_should_not_violate() {
      // WHEN
      EvaluationResult result =
          evaluate(
              HandledErrorRollbackFixtures.DefaultRulesCaller.class,
              HandledErrorRollbackFixtures.DefaultRulesBoundary.class,
              HandledErrorRollbackFixtures.BoundaryFailure.class);

      // THEN
      assertFalse(
          result.hasViolation(),
          "Spring does not roll back on a checked exception unless rollbackFor asks it to");
    }
  }

  @Nested
  @DisplayName("the reviewed allowlist")
  class ReviewedAllowlist {

    @Test
    @DisplayName("given a reviewed site, should carry a reason")
    void given_a_reviewed_site_should_carry_a_reason() {
      // THEN
      HandledErrorRollbackRules.REVIEWED_SITES.forEach(
          (site, reason) -> {
            assertTrue(site.contains("#") && site.contains(" -> "), "malformed site key: " + site);
            assertFalse(reason.isBlank(), "site without a reason: " + site);
          });
    }
  }
}
