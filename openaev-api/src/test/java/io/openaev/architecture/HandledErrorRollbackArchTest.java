package io.openaev.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Runs the #8102 guard over production classes: a handled error that dies at commit. The reviewed
 * sites live in {@link HandledErrorRollbackRules#REVIEWED_SITES}, each with its reason; a new one
 * fails the build and forces the decision.
 *
 * <p>{@link HandledErrorRollbackRulesTest} proves the rule fires, on fixtures.
 */
@AnalyzeClasses(packages = "io.openaev", importOptions = ImportOption.DoNotIncludeTests.class)
class HandledErrorRollbackArchTest {

  @ArchTest
  static final ArchRule handled_errors_that_die_at_commit_are_reviewed =
      HandledErrorRollbackRules.HANDLED_ERRORS_THAT_DIE_AT_COMMIT_ARE_REVIEWED;
}
