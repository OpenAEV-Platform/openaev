package io.openaev.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Every endpoint of production that names an inject as its resource loads that inject inside the
 * request's tenant scope, before it writes anything. See {@link InjectEndpointScopeGuardRules} for
 * what counts as an endpoint, a guard and a path to it; {@link InjectEndpointScopeGuardRulesTest}
 * proves the rule fires on fixtures.
 */
@AnalyzeClasses(packages = "io.openaev", importOptions = ImportOption.DoNotIncludeTests.class)
class InjectEndpointScopeGuardArchTest {

  @ArchTest
  static final ArchRule by_id_inject_endpoints_guard_their_resource_id_before_any_write =
      methods()
          .that(InjectEndpointScopeGuardRules.byIdInjectEndpoints())
          .should(InjectEndpointScopeGuardRules.guardTheResourceIdBeforeAnyWrite());
}
