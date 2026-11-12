package io.openaev.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.codeUnits;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Every production code unit that creates an inject, or gives it to a simulation or a scenario,
 * stamps the tenant of the inject itself. See {@link InjectCreationTenantRules} for what counts and
 * what is exempt; {@link InjectCreationTenantRulesTest} proves the rule fires on fixtures.
 */
@AnalyzeClasses(packages = "io.openaev", importOptions = ImportOption.DoNotIncludeTests.class)
class InjectCreationTenantArchTest {

  @ArchTest
  static final ArchRule injects_are_created_in_the_tenant_of_their_owner =
      codeUnits()
          .that(InjectCreationTenantRules.createOrAttachAnInject())
          .should(InjectCreationTenantRules.alsoStampTheTenant());
}
