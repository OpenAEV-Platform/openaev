package io.openaev.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.EvaluationResult;
import io.openaev.architecture.fixture.SelfInvocationTrapFixture;
import io.openaev.rest.PrimitiveOnHttpPathFixture;
import io.openaev.scheduler.jobs.RawTemplateJobFixture;
import io.openaev.scheduler.jobs.TransactionalJobFixture;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Proves each background-transaction guard actually fires, against fixtures that violate it (the
 * frozen variants in {@link TenantBackgroundTransactionArchTest} run on production classes and stay
 * green thanks to the committed baseline). Evaluated through {@code evaluate().hasViolation()} so a
 * pass can only come from a REAL violation, never from ArchUnit's empty-should error. Also pins the
 * freeze-store lock: a test run must not be able to rewrite the recorded baseline.
 */
@DisplayName("Background transaction guards: each rule fires, and the freeze store is locked")
class TenantBackgroundTransactionRulesTest {

  @Test
  @DisplayName("an intra-class call to a @Transactional method violates the self-invocation rule")
  void selfInvocationFixtureViolates() {
    JavaClasses fixture = new ClassFileImporter().importClasses(SelfInvocationTrapFixture.class);
    EvaluationResult result =
        TenantBackgroundTransactionRules.NO_TRANSACTIONAL_SELF_INVOCATION.evaluate(fixture);
    assertTrue(result.hasViolation(), "the rule must flag the fixture's intra-class call");
    assertTrue(
        result.getFailureReport().toString().contains("SelfInvocationTrapFixture"),
        "the violation must point at the fixture");
  }

  @Test
  @DisplayName("a @Transactional method on a job class violates the jobs rule")
  void transactionalJobFixtureViolates() {
    JavaClasses fixture = new ClassFileImporter().importClasses(TransactionalJobFixture.class);
    EvaluationResult result =
        TenantBackgroundTransactionRules.NO_TRANSACTIONAL_IN_JOBS.evaluate(fixture);
    assertTrue(result.hasViolation(), "the rule must flag the fixture's @Transactional method");
    assertTrue(
        result.getFailureReport().toString().contains("TransactionalJobFixture"),
        "the violation must point at the fixture");
  }

  @Test
  @DisplayName("a raw TransactionTemplate in a job class violates the plumbing rule")
  void rawTemplateJobFixtureViolates() {
    JavaClasses fixture = new ClassFileImporter().importClasses(RawTemplateJobFixture.class);
    EvaluationResult result =
        TenantBackgroundTransactionRules.NO_RAW_TRANSACTION_PLUMBING_IN_JOBS.evaluate(fixture);
    assertTrue(result.hasViolation(), "the rule must flag the fixture's raw TransactionTemplate");
    assertTrue(
        result.getFailureReport().toString().contains("RawTemplateJobFixture"),
        "the violation must point at the fixture");
  }

  @Test
  @DisplayName("an HTTP-side class reaching for the primitive violates the HTTP-path rule")
  void primitiveOnHttpPathFixtureViolates() {
    JavaClasses fixture = new ClassFileImporter().importClasses(PrimitiveOnHttpPathFixture.class);
    EvaluationResult result =
        TenantBackgroundTransactionRules.NO_PRIMITIVE_ON_HTTP_PATH.evaluate(fixture);
    assertTrue(result.hasViolation(), "the rule must flag the fixture's primitive dependency");
    assertTrue(
        result.getFailureReport().toString().contains("PrimitiveOnHttpPathFixture"),
        "the violation must point at the fixture");
  }

  @Test
  @DisplayName("the out-of-package controllers are covered by the HTTP-side predicate")
  void outOfPackageControllersAreHttpSide() {
    // The proven holes of package-only matching: three real controllers live outside rest../api..
    JavaClasses controllers =
        new ClassFileImporter()
            .importClasses(
                io.openaev.opencti.OpenCTIApi.class,
                io.openaev.schema.SchemaApi.class,
                io.openaev.search.FullTextSearchApi.class);
    for (com.tngtech.archunit.core.domain.JavaClass controller : controllers) {
      assertTrue(
          TenantBackgroundTransactionRules.HTTP_SIDE_CLASSES.test(controller),
          controller.getName() + " must be matched as HTTP-side");
    }
  }

  @Test
  @DisplayName("the freeze store is locked: a test run cannot rewrite the baseline")
  void freezeStoreIsLocked() throws IOException {
    Properties archunit = new Properties();
    try (InputStream config =
        getClass().getClassLoader().getResourceAsStream("archunit.properties")) {
      archunit.load(config);
    }
    assertTrue(
        "false".equals(archunit.getProperty("freeze.store.default.allowStoreUpdate")),
        "the freeze store must be read-only for test runs (allowStoreUpdate=false)");
    assertTrue(
        "false".equals(archunit.getProperty("freeze.store.default.allowStoreCreation")),
        "the freeze store must not be re-creatable by test runs (allowStoreCreation=false)");
  }
}
