package io.openaev.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.data.spel.spi.EvaluationContextExtension;

/**
 * No repository query reads the tenant through a SpEL variable any more: the {@code #tenantContext}
 * extension is gone, and a new one would put the ambient tenant back into a query. {@link
 * TenantSpelExtensionRemovedRulesTest} proves the rule fires on fixtures.
 */
@AnalyzeClasses(packages = "io.openaev", importOptions = ImportOption.DoNotIncludeTests.class)
class TenantSpelExtensionRemovedArchTest {

  @ArchTest
  static final ArchRule no_class_provides_a_spel_evaluation_context_extension =
      noClasses()
          .should()
          .dependOnClassesThat()
          .areAssignableTo(EvaluationContextExtension.class)
          .because(
              "a query that reads the tenant from a SpEL variable bypasses the explicit scope; the"
                  + " tenant is a method argument or the statement inspector's");
}
