package io.openaev.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.FreezingArchRule;

/**
 * Frozen-baseline guards for the background transaction decision (#6398, widened #6391): the
 * existing violations are recorded in the committed freeze store ({@code
 * src/test/resources/archunit_store}, the future conversion work list) and only a NEW violation
 * breaks the build. The store is locked: {@code freeze.store.default.allowStoreUpdate} is false, so
 * a test run cannot rewrite the baseline.
 *
 * <p>{@link TenantBackgroundTransactionRulesTest} proves each rule fires, on fixtures.
 */
@AnalyzeClasses(packages = "io.openaev", importOptions = ImportOption.DoNotIncludeTests.class)
class TenantBackgroundTransactionArchTest {

  @ArchTest
  static final ArchRule no_transactional_self_invocation =
      FreezingArchRule.freeze(TenantBackgroundTransactionRules.NO_TRANSACTIONAL_SELF_INVOCATION);

  @ArchTest
  static final ArchRule no_transactional_in_background_jobs =
      FreezingArchRule.freeze(TenantBackgroundTransactionRules.NO_TRANSACTIONAL_IN_JOBS);

  @ArchTest
  static final ArchRule no_raw_transaction_plumbing_in_background_jobs =
      FreezingArchRule.freeze(TenantBackgroundTransactionRules.NO_RAW_TRANSACTION_PLUMBING_IN_JOBS);

  @ArchTest
  static final ArchRule no_primitive_on_the_http_path =
      FreezingArchRule.freeze(TenantBackgroundTransactionRules.NO_PRIMITIVE_ON_HTTP_PATH);
}
