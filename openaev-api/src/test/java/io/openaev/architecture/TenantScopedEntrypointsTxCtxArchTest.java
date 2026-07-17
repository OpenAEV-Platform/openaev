package io.openaev.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import io.openaev.context.TxCtx;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Guards the v2 tenant-scoped entrypoints against accidental signature drift (Corinne, #6255). The
 * {@code TxCtx} parameter is how the tenant scope reaches the transaction aspect; it looks unused
 * in the handler, so a refactor could drop it. This test fails the build if any listed entrypoint
 * loses its {@code TxCtx}.
 *
 * <p>This is a floor, not the robust fix: the entrypoint list is hardcoded, so it protects against
 * deletion on a known method but not against a new scoped endpoint added without {@code TxCtx}. The
 * self-maintaining version (a {@code @RequiresTxScope} marker plus a compile-time rule in
 * openaev-annotation-processor) is tracked as a follow-up.
 */
@AnalyzeClasses(packages = "io.openaev", importOptions = ImportOption.DoNotIncludeTests.class)
class TenantScopedEntrypointsTxCtxArchTest {

  private static final Set<String> TX_SCOPED_ENTRYPOINTS =
      Set.of(
          // import_mappers (v2)
          "io.openaev.rest.mapper.MapperApi#getImportMapper",
          "io.openaev.rest.mapper.MapperApi#getImportMapperById",
          "io.openaev.rest.mapper.MapperApi#createImportMapper",
          "io.openaev.rest.mapper.MapperApi#exportMappers",
          "io.openaev.rest.mapper.MapperApi#importMappers",
          "io.openaev.rest.mapper.MapperApi#duplicateMapper",
          "io.openaev.rest.mapper.MapperApi#updateImportMapper",
          "io.openaev.rest.mapper.MapperApi#deleteImportMapper",
          "io.openaev.rest.lessons_template.LessonsTemplateApi#createLessonsTemplate",
          "io.openaev.rest.lessons_template.LessonsTemplateApi#lessonsTemplates",
          "io.openaev.rest.lessons_template.LessonsTemplateApi#updateLessonsTemplate",
          "io.openaev.rest.lessons_template.LessonsTemplateApi#deleteLessonsTemplate",
          "io.openaev.rest.lessons_template.LessonsTemplateApi#createLessonsTemplateCategory",
          "io.openaev.rest.lessons.ScenarioLessonsApi#applyScenarioLessonsTemplate",
          "io.openaev.rest.lessons.ExerciseLessonsApi#applyExerciseLessonsTemplate",
          "io.openaev.rest.scenario.ScenarioImportApi#dryRunImportXLSFile",
          "io.openaev.rest.scenario.ScenarioImportApi#validateImportXLSFile",
          "io.openaev.rest.exercise.ExerciseImportApi#dryRunImportXLSFile",
          "io.openaev.rest.exercise.ExerciseImportApi#validateImportXLSFile",
          // cwes: reached through a vulnerability's @ManyToMany, so every vulnerability/CVE
          // read-or-write entrypoint that maps the association carries the scope.
          "io.openaev.rest.vulnerability.VulnerabilityApi#searchVulnerabilities",
          "io.openaev.rest.vulnerability.VulnerabilityApi#getVulnerability",
          "io.openaev.rest.vulnerability.VulnerabilityApi#getVulnerabilityByExternalId",
          "io.openaev.rest.vulnerability.VulnerabilityApi#createVulnerability",
          "io.openaev.rest.vulnerability.VulnerabilityApi#bulkInsertVulnerabilitiesForCollector",
          "io.openaev.rest.vulnerability.VulnerabilityApi#updateVulnerability",
          "io.openaev.rest.cve.CveApi#searchCves",
          "io.openaev.rest.cve.CveApi#getCve",
          "io.openaev.rest.cve.CveApi#getCvebyExternalId",
          "io.openaev.rest.cve.CveApi#createCve",
          "io.openaev.rest.cve.CveApi#bulkInsertCVEsForCollector",
          "io.openaev.rest.cve.CveApi#updateCve",
          // mitigations (v2)
          "io.openaev.rest.mitigation.MitigationApi#mitigations",
          "io.openaev.rest.mitigation.MitigationApi#mitigation",
          "io.openaev.rest.mitigation.MitigationApi#injectorContracts",
          "io.openaev.rest.mitigation.MitigationApi#createMitigation",
          "io.openaev.rest.mitigation.MitigationApi#updateMitigation",
          "io.openaev.rest.mitigation.MitigationApi#upsertMitigation",
          "io.openaev.rest.mitigation.MitigationApi#deleteMitigation");

  @ArchTest
  static final ArchRule tx_scoped_entrypoints_must_declare_tx_ctx =
      methods()
          .that(
              new DescribedPredicate<JavaMethod>("are v2 tenant-scoped entrypoints") {
                @Override
                public boolean test(JavaMethod method) {
                  return TX_SCOPED_ENTRYPOINTS.contains(
                      method.getOwner().getName() + "#" + method.getName());
                }
              })
          .should(
              new ArchCondition<JavaMethod>("declare a TxCtx parameter") {
                @Override
                public void check(JavaMethod method, ConditionEvents events) {
                  boolean hasTxCtx =
                      method.getRawParameterTypes().stream()
                          .anyMatch(type -> TxCtx.class.getName().equals(type.getName()));
                  if (!hasTxCtx) {
                    String parameterList =
                        method.getRawParameterTypes().stream()
                            .map(javaClass -> javaClass.getName())
                            .collect(Collectors.joining(", "));
                    events.add(
                        SimpleConditionEvent.violated(
                            method,
                            "Entrypoint "
                                + method.getOwner().getName()
                                + "#"
                                + method.getName()
                                + "("
                                + parameterList
                                + ") must declare TxCtx to activate the tenant v2 transaction scope"));
                  }
                }
              });
}
