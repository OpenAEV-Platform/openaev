package io.openaev.service.stix;

import static io.openaev.rest.payload.service.PayloadService.DYNAMIC_DNS_RESOLUTION_HOSTNAME_KEY;
import static io.openaev.utils.fixtures.InjectExpectationResultFixture.createCollectorResult;
import static io.openaev.utils.fixtures.InjectExpectationResultFixture.createManualResult;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.config.OpenAEVConfig;
import io.openaev.context.TenantContext;
import io.openaev.database.model.*;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.PreviewFeatureService;
import io.openaev.service.SecurityCoverageSendJobService;
import io.openaev.stix.objects.Bundle;
import io.openaev.stix.objects.DomainObject;
import io.openaev.stix.objects.ObjectBase;
import io.openaev.stix.objects.RelationshipObject;
import io.openaev.stix.objects.constants.CommonProperties;
import io.openaev.stix.objects.constants.ExtendedProperties;
import io.openaev.stix.objects.constants.ObjectTypes;
import io.openaev.stix.parsing.Parser;
import io.openaev.stix.parsing.ParsingException;
import io.openaev.stix.types.*;
import io.openaev.utils.InjectExpectationResultUtils;
import io.openaev.utils.ResultUtils;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.*;
import io.openaev.utils.fixtures.files.AttackPatternFixture;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import net.javacrumbs.jsonunit.core.Option;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@ExtendWith(MockitoExtension.class)
// Lenient, as the Boot 3 MockitoTestExecutionListener that used to init these mocks was.
@MockitoSettings(strictness = Strictness.LENIENT)
public class SecurityCoverageServiceTest extends IntegrationTest {

  @Autowired private SecurityCoverageService securityCoverageService;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectExpectationComposer injectExpectationComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private AgentComposer agentComposer;
  @Autowired private SecurityCoverageComposer securityCoverageComposer;
  @Autowired private SecurityCoverageSendJobComposer securityCoverageSendJobComposer;
  @Autowired private InjectorFixture injectorFixture;
  @Autowired private AttackPatternComposer attackPatternComposer;
  @Autowired private VulnerabilityComposer vulnerabilityComposer;
  @Autowired private SecurityPlatformComposer securityPlatformComposer;
  @Autowired private EntityManager entityManager;
  @Autowired private SecurityCoverageSendJobService securityCoverageSendJobService;
  @Autowired private ObjectMapper mapper;
  @Autowired private ResultUtils resultUtils;
  @Autowired private OpenAEVConfig openAEVConfig;
  private Parser stixParser;
  @Mock private PreviewFeatureService previewFeatureService;

  @BeforeEach
  public void setup() {
    exerciseComposer.reset();
    injectComposer.reset();
    injectExpectationComposer.reset();
    injectorContractComposer.reset();
    attackPatternComposer.reset();
    vulnerabilityComposer.reset();
    securityCoverageComposer.reset();
    scenarioComposer.reset();
    securityPlatformComposer.reset();
    securityCoverageSendJobComposer.reset();

    when(previewFeatureService.isFeatureEnabled(PreviewFeature.TENANT_FIELDS_FOR_SECURITY_COVERAGE))
        .thenReturn(true);

    stixParser = new Parser(mapper);
  }

  /*
   * attackPatternWrappers: map of attack pattern, isCovered bool
   * vulnerabilityWrappers: map of vulnerability, isCovered bool
   * set isCovered to true if there should be an inject covering this attack pattern
   * otherwise, false means the attack pattern will be "uncovered"
   */
  private ExerciseComposer.Composer createExerciseWrapperWithInjectsForDomainObjects(
      Map<AttackPatternComposer.Composer, java.lang.Boolean> attackPatternWrappers,
      Map<VulnerabilityComposer.Composer, java.lang.Boolean> vulnWrappers) {

    // ensure attack patterns have IDs
    attackPatternWrappers.keySet().forEach(AttackPatternComposer.Composer::persist);
    // ensure vulns have IDs
    vulnWrappers.keySet().forEach(VulnerabilityComposer.Composer::persist);

    List<AttackPattern> attackPatternList =
        attackPatternWrappers.keySet().stream().map(AttackPatternComposer.Composer::get).toList();
    List<Vulnerability> vulnerabilities =
        vulnWrappers.keySet().stream().map(VulnerabilityComposer.Composer::get).toList();

    ExerciseComposer.Composer exerciseWrapper =
        exerciseComposer
            .forExercise(ExerciseFixture.createDefaultExercise())
            .withSecurityCoverage(
                securityCoverageComposer.forSecurityCoverage(
                    SecurityCoverageFixture.createSecurityCoverageWithDomainObjects(
                        attackPatternList, vulnerabilities)));

    exerciseWrapper.get().setStatus(ExerciseStatus.FINISHED);

    for (Map.Entry<AttackPatternComposer.Composer, java.lang.Boolean> apw :
        attackPatternWrappers.entrySet()) {
      if (apw.getValue()) { // this attack pattern should be covered
        exerciseWrapper.withInject(
            injectComposer
                .forInject(InjectFixture.getDefaultInject())
                .withInjectorContract(
                    injectorContractComposer
                        .forInjectorContract(
                            InjectorContractFixture.createDefaultInjectorContract())
                        .withInjector(injectorFixture.getWellKnownOaevImplantInjector())
                        .withAttackPattern(apw.getKey()))
                .withExpectation(
                    injectExpectationComposer
                        .forExpectation(
                            InjectExpectationFixture.createExpectationWithTypeAndStatus(
                                BaseInjectExpectation.EXPECTATION_TYPE.DETECTION,
                                BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS))
                        .withEndpoint(
                            endpointComposer.forEndpoint(EndpointFixture.createEndpoint())))
                .withExpectation(
                    injectExpectationComposer
                        .forExpectation(
                            InjectExpectationFixture.createExpectationWithTypeAndStatus(
                                BaseInjectExpectation.EXPECTATION_TYPE.PREVENTION,
                                BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS))
                        .withEndpoint(
                            endpointComposer.forEndpoint(EndpointFixture.createEndpoint()))));
      }
    }

    for (Map.Entry<VulnerabilityComposer.Composer, java.lang.Boolean> vulnw :
        vulnWrappers.entrySet()) {
      if (vulnw.getValue()) { // this vuln should be covered
        exerciseWrapper.withInject(
            injectComposer
                .forInject(InjectFixture.getDefaultInject())
                .withInjectorContract(
                    injectorContractComposer
                        .forInjectorContract(
                            InjectorContractFixture.createDefaultInjectorContract())
                        .withInjector(injectorFixture.getWellKnownOaevImplantInjector())
                        .withVulnerability(vulnw.getKey()))
                .withExpectation(
                    injectExpectationComposer
                        .forExpectation(
                            InjectExpectationFixture.createExpectationWithTypeAndStatus(
                                BaseInjectExpectation.EXPECTATION_TYPE.VULNERABILITY,
                                BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS))
                        .withEndpoint(
                            endpointComposer.forEndpoint(EndpointFixture.createEndpoint()))));
      }
    }
    return exerciseWrapper;
  }

  private DomainObject addPropertiesToDomainObject(
      DomainObject obj, Map<String, BaseType<?>> props) {
    for (Map.Entry<String, BaseType<?>> entry : props.entrySet()) {
      obj.setProperty(entry.getKey(), entry.getValue());
    }
    return obj;
  }

  private io.openaev.stix.types.List<Complex<CoverageResult>> predictCoverageFromInjects(
      List<Inject> injects) {
    List<InjectExpectationResultUtils.ExpectationResultsByType> results =
        resultUtils.computeGlobalExpectationResults(
            injects.stream().map(Inject::getId).collect(Collectors.toSet()));
    return toList(
        results.stream()
            .map(
                r ->
                    new Complex<>(
                        new CoverageResult(
                            r.type().name(), (int) Math.round(r.getSuccessRate() * 100))))
            .toList());
  }

  private <T extends BaseType<?>> io.openaev.stix.types.List<T> toList(List<T> innerList) {
    return new io.openaev.stix.types.List<>(innerList);
  }

  private DomainObject getExpectedMainSecurityCoverage(
      SecurityCoverage securityCoverage, List<Inject> injects)
      throws ParsingException, JsonProcessingException {
    return addPropertiesToDomainObject(
        (DomainObject) stixParser.parseObject(securityCoverage.getContent()),
        Map.of(ExtendedProperties.COVERAGE.toString(), predictCoverageFromInjects(injects)));
  }

  @Nested
  @DisplayName("All Domain Objects are covered and all expectations are successul")
  class AllDomainObjectsCoveredAndAllExpectationsAreSuccessful {

    private void setupSuccessfulExpectations(
        SecurityPlatformComposer.Composer securityPlatformWrapper) {
      injectExpectationComposer.generatedItems.forEach(
          exp ->
              exp.setResults(
                  List.of(
                      InjectExpectationResult.builder()
                          .score(100.0)
                          .sourceId(securityPlatformWrapper.get().getId())
                          .sourceName("Unit Tests")
                          .sourceType("manual")
                          .sourcePlatform(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name())
                          .sourceAssetId(UUID.randomUUID().toString())
                          .build())));
    }

    private void persistScenario(ExerciseComposer.Composer exerciseWrapper) {
      scenarioComposer
          .forScenario(ScenarioFixture.createDefaultCrisisScenario())
          .withSimulation(exerciseWrapper)
          .persist();
      entityManager.flush();
      entityManager.refresh(exerciseWrapper.get());
    }

    private void assertMainAssessment(
        Bundle bundle, SecurityCoverage generatedCoverage, DomainObject expectedAssessment)
        throws ParsingException {

      assertThatJson(
              bundle.findById(new Identifier(generatedCoverage.getExternalId())).toStix(mapper))
          .whenIgnoringPaths(
              CommonProperties.MODIFIED.toString(),
              CommonProperties.EXTERNAL_URI.toString(),
              CommonProperties.TENANT_ID.toString(),
              CommonProperties.TENANT_NAME.toString(),
              CommonProperties.AUTO_ENRICHMENT_DISABLE.toString())
          .isEqualTo(expectedAssessment.toStix(mapper));
    }

    @Test
    @DisplayName(
        "When all attack patterns are covered and all expectations are successful, bundle is correct")
    public void whenAllAttackPatternsAreCoveredAndAllExpectationsAreSuccessful_bundleIsCorrect()
        throws ParsingException, JsonProcessingException {
      AttackPatternComposer.Composer ap1 =
          attackPatternComposer.forAttackPattern(
              AttackPatternFixture.createAttackPatternsWithExternalId("T1234"));
      AttackPatternComposer.Composer ap2 =
          attackPatternComposer.forAttackPattern(
              AttackPatternFixture.createAttackPatternsWithExternalId("T5678"));
      // some security platforms
      SecurityPlatformComposer.Composer securityPlatformWrapper =
          securityPlatformComposer
              .forSecurityPlatform(
                  SecurityPlatformFixture.createDefault(
                      "Bad EDR", SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name()))
              .persist();
      // another nameless platform not involved in simulation
      securityPlatformComposer
          .forSecurityPlatform(
              SecurityPlatformFixture.createDefault(
                  "New SIEM", SecurityPlatform.SECURITY_PLATFORM_TYPE.SIEM.name()))
          .persist();
      // create exercise cover all TTPs
      ExerciseComposer.Composer exerciseWrapper =
          createExerciseWrapperWithInjectsForDomainObjects(Map.of(ap1, true, ap2, true), Map.of());
      exerciseWrapper.get().setStatus(ExerciseStatus.FINISHED);

      // set SUCCESS results for all inject expectations
      setupSuccessfulExpectations(securityPlatformWrapper);
      persistScenario(exerciseWrapper);

      Optional<SecurityCoverageSendJob> job =
          securityCoverageSendJobService.createOrUpdateCoverageSendJobForSimulationIfReady(
              exerciseWrapper.get());

      // intermediate assert
      assertThat(job).isNotEmpty();

      // act
      Bundle bundle = securityCoverageService.createBundleFromSendJobs(List.of(job.orElseThrow()));

      // assert
      SecurityCoverage generatedCoverage = securityCoverageComposer.generatedItems.getFirst();
      SecurityCoverage coverage = securityCoverageComposer.generatedItems.getFirst();
      DomainObject expectedAssessmentWithCoverage =
          getExpectedMainSecurityCoverage(coverage, injectComposer.generatedItems);

      List<DomainObject> expectedPlatformIdentities = getExpectedPlatformIdentities();

      // main assessment is completed with coverage
      assertMainAssessment(bundle, generatedCoverage, expectedAssessmentWithCoverage);

      // security platforms are present in bundle as Identities
      for (DomainObject platformSdo : expectedPlatformIdentities) {
        assertThatJson(bundle.findById(platformSdo.getId()).toStix(mapper))
            .isEqualTo(platformSdo.toStix(mapper));

        // security platform SROs
        List<RelationshipObject> actualSros =
            bundle.findRelationshipsByTargetRef(platformSdo.getId());
        assertThat(actualSros.size()).isEqualTo(1);

        RelationshipObject actualSro = actualSros.getFirst();
        RelationshipObject expectedSro =
            new RelationshipObject(
                Map.of(
                    CommonProperties.ID.toString(),
                    new Identifier(
                        ObjectTypes.RELATIONSHIP.toString(), UUID.randomUUID().toString()),
                    CommonProperties.TYPE.toString(),
                    new StixString(ObjectTypes.RELATIONSHIP.toString()),
                    RelationshipObject.Properties.RELATIONSHIP_TYPE.toString(),
                    new StixString("has-covered"),
                    RelationshipObject.Properties.SOURCE_REF.toString(),
                    expectedAssessmentWithCoverage.getId(),
                    RelationshipObject.Properties.TARGET_REF.toString(),
                    platformSdo.getId(),
                    ExtendedProperties.COVERED.toString(),
                    new io.openaev.stix.types.Boolean(true),
                    CommonProperties.EXTERNAL_URI.toString(),
                    new StixString(
                        openAEVConfig.getBaseUrl()
                            + "/"
                            + TenantContext.getCurrentTenant()
                            + "/admin/simulations/"
                            + exerciseWrapper.get().getScenario().getId()),
                    ExtendedProperties.COVERAGE.toString(),
                    toList(
                        List.of(
                            new Complex<>(
                                new CoverageResult(
                                    "PREVENTION",
                                    platformSdo
                                            .getId()
                                            .getValue()
                                            .contains(securityPlatformWrapper.get().getId())
                                        ? 100
                                        : 0)),
                            new Complex<>(
                                new CoverageResult(
                                    "DETECTION",
                                    platformSdo
                                            .getId()
                                            .getValue()
                                            .contains(securityPlatformWrapper.get().getId())
                                        ? 100
                                        : 0))))));
        assertThatJson(actualSro.toStix(mapper))
            .whenIgnoringPaths(
                CommonProperties.ID.toString(), CommonProperties.EXTERNAL_URI.toString())
            .isEqualTo(expectedSro.toStix(mapper));
      }

      // attack pattern SROs
      for (StixRefToExternalRef stixRef : generatedCoverage.getAttackPatternRefs()) {
        List<RelationshipObject> actualSros =
            bundle.findRelationshipsByTargetRef(new Identifier(stixRef.getStixRef()));
        assertThat(actualSros.size()).isEqualTo(1);

        RelationshipObject actualSro = actualSros.getFirst();
        RelationshipObject expectedSro =
            new RelationshipObject(
                Map.of(
                    CommonProperties.ID.toString(),
                    new Identifier(
                        ObjectTypes.RELATIONSHIP.toString(), UUID.randomUUID().toString()),
                    CommonProperties.TYPE.toString(),
                    new StixString(ObjectTypes.RELATIONSHIP.toString()),
                    RelationshipObject.Properties.RELATIONSHIP_TYPE.toString(),
                    new StixString("has-covered"),
                    RelationshipObject.Properties.SOURCE_REF.toString(),
                    expectedAssessmentWithCoverage.getId(),
                    RelationshipObject.Properties.TARGET_REF.toString(),
                    new Identifier(stixRef.getStixRef()),
                    ExtendedProperties.COVERED.toString(),
                    new io.openaev.stix.types.Boolean(true),
                    CommonProperties.EXTERNAL_URI.toString(),
                    new StixString(
                        openAEVConfig.getBaseUrl()
                            + "/"
                            + TenantContext.getCurrentTenant()
                            + "/admin/scenarios/"
                            + exerciseWrapper.get().getScenario().getId()),
                    ExtendedProperties.COVERAGE.toString(),
                    toList(
                        List.of(
                            new Complex<>(new CoverageResult("PREVENTION", 100)),
                            new Complex<>(new CoverageResult("DETECTION", 100))))));
        assertThatJson(actualSro.toStix(mapper))
            .whenIgnoringPaths(
                CommonProperties.ID.toString(), CommonProperties.EXTERNAL_URI.toString())
            .isEqualTo(expectedSro.toStix(mapper));
      }
    }

    @Nested
    @DisplayName("With enabled preview feature: STIX_SECURITY_COVERAGE_FOR_VULNERABILITIES")
    @Disabled(
        "Disabled as long as needing preview feature STIX_SECURITY_COVERAGE_FOR_VULNERABILITIES")
    public class withEnabledPreviewFeature {
      @Test
      @DisplayName(
          "When all vulnerabilities are covered and all expectations are successful, bundle is correct")
      public void whenAllVulnerabilitiesAreCoveredAndAllExpectationsAreSuccessful_bundleIsCorrect()
          throws ParsingException, JsonProcessingException {
        VulnerabilityComposer.Composer vuln1 =
            vulnerabilityComposer.forVulnerability(
                VulnerabilityFixture.createVulnerabilityInput("CVE-1234-5678"));
        // create exercise cover all TTPs
        ExerciseComposer.Composer exerciseWrapper =
            createExerciseWrapperWithInjectsForDomainObjects(Map.of(), Map.of(vuln1, true));
        exerciseWrapper.get().setStatus(ExerciseStatus.FINISHED);

        persistScenario(exerciseWrapper);

        Optional<SecurityCoverageSendJob> job =
            securityCoverageSendJobService.createOrUpdateCoverageSendJobForSimulationIfReady(
                exerciseWrapper.get());

        // intermediate assert
        assertThat(job).isNotEmpty();

        // act
        Bundle bundle =
            securityCoverageService.createBundleFromSendJobs(List.of(job.orElseThrow()));

        // assert
        SecurityCoverage generatedCoverage = securityCoverageComposer.generatedItems.getFirst();
        SecurityCoverage coverage = securityCoverageComposer.generatedItems.getFirst();
        DomainObject expectedAssessmentWithCoverage =
            getExpectedMainSecurityCoverage(coverage, injectComposer.generatedItems);

        // main assessment is completed with coverage
        assertMainAssessment(bundle, generatedCoverage, expectedAssessmentWithCoverage);

        // vulnerabilities SROs
        for (StixRefToExternalRef stixRef : generatedCoverage.getVulnerabilitiesRefs()) {
          List<RelationshipObject> actualSros =
              bundle.findRelationshipsByTargetRef(new Identifier(stixRef.getStixRef()));
          assertThat(actualSros.size()).isEqualTo(1);

          RelationshipObject actualSro = actualSros.getFirst();
          RelationshipObject expectedSro =
              new RelationshipObject(
                  Map.of(
                      CommonProperties.ID.toString(),
                      new Identifier(
                          ObjectTypes.RELATIONSHIP.toString(), UUID.randomUUID().toString()),
                      CommonProperties.TYPE.toString(),
                      new StixString(ObjectTypes.RELATIONSHIP.toString()),
                      RelationshipObject.Properties.RELATIONSHIP_TYPE.toString(),
                      new StixString("has-covered"),
                      RelationshipObject.Properties.SOURCE_REF.toString(),
                      expectedAssessmentWithCoverage.getId(),
                      RelationshipObject.Properties.TARGET_REF.toString(),
                      new Identifier(stixRef.getStixRef()),
                      ExtendedProperties.COVERED.toString(),
                      new io.openaev.stix.types.Boolean(true),
                      CommonProperties.EXTERNAL_URI.toString(),
                      new StixString(
                          openAEVConfig.getBaseUrl()
                              + "/"
                              + TenantContext.getCurrentTenant()
                              + "/admin/scenarios/"
                              + exerciseWrapper.get().getScenario().getId()),
                      ExtendedProperties.COVERAGE.toString(),
                      toList(List.of(new Complex<>(new CoverageResult("VULNERABILITY", 100))))));
          assertThatJson(actualSro.toStix(mapper))
              .whenIgnoringPaths(CommonProperties.ID.toString())
              .isEqualTo(expectedSro.toStix(mapper));
        }
      }
    }

    @Nested
    @DisplayName("Without preview feature: STIX_SECURITY_COVERAGE_FOR_VULNERABILITIES")
    public class WithoutPreviewFeature {
      @Test
      @DisplayName(
          "When all vulnerabilities are covered and all expectations are successful, bundle is correct")
      public void whenAllVulnerabilitiesAreCoveredAndAllExpectationsAreSuccessful_bundleIsCorrect()
          throws ParsingException, JsonProcessingException {
        VulnerabilityComposer.Composer vuln1 =
            vulnerabilityComposer.forVulnerability(
                VulnerabilityFixture.createVulnerabilityInput("CVE-1234-5678"));
        // create exercise cover all TTPs
        ExerciseComposer.Composer exerciseWrapper =
            createExerciseWrapperWithInjectsForDomainObjects(Map.of(), Map.of(vuln1, true));
        exerciseWrapper.get().setStatus(ExerciseStatus.FINISHED);

        persistScenario(exerciseWrapper);

        Optional<SecurityCoverageSendJob> job =
            securityCoverageSendJobService.createOrUpdateCoverageSendJobForSimulationIfReady(
                exerciseWrapper.get());

        // intermediate assert
        assertThat(job).isNotEmpty();

        // act
        Bundle bundle = securityCoverageService.createBundleFromSendJobs(List.of(job.get()));

        // assert
        SecurityCoverage generatedCoverage = securityCoverageComposer.generatedItems.getFirst();
        SecurityCoverage coverage = securityCoverageComposer.generatedItems.getFirst();
        DomainObject expectedAssessmentWithCoverage =
            getExpectedMainSecurityCoverage(coverage, injectComposer.generatedItems);

        // main assessment is completed with coverage
        assertMainAssessment(bundle, generatedCoverage, expectedAssessmentWithCoverage);

        // vulnerabilities SROs
        for (StixRefToExternalRef stixRef : generatedCoverage.getVulnerabilitiesRefs()) {
          List<RelationshipObject> actualSros =
              bundle.findRelationshipsByTargetRef(new Identifier(stixRef.getStixRef()));
          assertThat(actualSros.size()).isEqualTo(0);
        }
      }

      @Test
      @DisplayName("Multiple bundles are created should have the same SRO ID")
      public void whenMultipleBundlesAreCreatedShouldHaveTheSameSROID()
          throws ParsingException, JsonProcessingException {
        AttackPatternComposer.Composer ap1 =
            attackPatternComposer.forAttackPattern(
                AttackPatternFixture.createAttackPatternsWithExternalId("T1234"));
        AttackPatternComposer.Composer ap2 =
            attackPatternComposer.forAttackPattern(
                AttackPatternFixture.createAttackPatternsWithExternalId("T5678"));
        // some security platforms
        SecurityPlatformComposer.Composer securityPlatformWrapper =
            securityPlatformComposer
                .forSecurityPlatform(
                    SecurityPlatformFixture.createDefault(
                        "Bad EDR", SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name()))
                .persist();
        // another nameless platform not involved in simulation
        securityPlatformComposer
            .forSecurityPlatform(
                SecurityPlatformFixture.createDefault(
                    "New SIEM", SecurityPlatform.SECURITY_PLATFORM_TYPE.SIEM.name()))
            .persist();
        // create exercise cover all TTPs
        ExerciseComposer.Composer exerciseWrapper =
            createExerciseWrapperWithInjectsForDomainObjects(
                Map.of(ap1, true, ap2, true), Map.of());
        exerciseWrapper.get().setStatus(ExerciseStatus.FINISHED);

        // set SUCCESS results for all inject expectations
        setupSuccessfulExpectations(securityPlatformWrapper);
        persistScenario(exerciseWrapper);

        Optional<SecurityCoverageSendJob> job =
            securityCoverageSendJobService.createOrUpdateCoverageSendJobForSimulationIfReady(
                exerciseWrapper.get());

        // intermediate assert
        assertThat(job).isNotEmpty();

        // act
        Bundle bundle1 =
            securityCoverageService.createBundleFromSendJobs(List.of(job.orElseThrow()));
        Bundle bundle2 =
            securityCoverageService.createBundleFromSendJobs(List.of(job.orElseThrow()));

        // assert
        assertThat(bundle1).isNotNull();
        assertThat(bundle2).isNotNull();

        List<String> sroIds1 =
            bundle1.getRelationshipObjects().stream()
                .filter(sro -> sro.hasProperty("id"))
                .map(sro -> sro.getProperty("id").getValue().toString())
                .toList();
        List<String> sroIds2 =
            bundle2.getRelationshipObjects().stream()
                .filter(sro -> sro.hasProperty("id"))
                .map(sro -> sro.getProperty("id").getValue().toString())
                .toList();

        assertThat(sroIds1).containsExactlyInAnyOrderElementsOf(sroIds2);
      }
    }
  }

  @Test
  @DisplayName(
      "When all attack patterns are covered and half of expectations are successful, bundle is correct")
  public void whenAllAttackPatternsAreCoveredAndHalfOfAllExpectationsAreSuccessful_bundleIsCorrect()
      throws ParsingException, JsonProcessingException {
    Instant simulationStartTime = Instant.parse("2024-09-23T14:09:43Z");
    Instant nextSimulationStartTime = Instant.parse("2024-09-24T14:09:43Z");
    AttackPatternComposer.Composer ap1 =
        attackPatternComposer.forAttackPattern(
            AttackPatternFixture.createAttackPatternsWithExternalId("T1234"));
    AttackPatternComposer.Composer ap2 =
        attackPatternComposer.forAttackPattern(
            AttackPatternFixture.createAttackPatternsWithExternalId("T5678"));
    SecurityPlatformComposer.Composer securityPlatformWrapper =
        securityPlatformComposer
            .forSecurityPlatform(
                SecurityPlatformFixture.createDefault(
                    "Bad EDR", SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name()))
            .persist();
    // another nameless platform not involved in simulation
    securityPlatformComposer
        .forSecurityPlatform(
            SecurityPlatformFixture.createDefault(
                "New SIEM", SecurityPlatform.SECURITY_PLATFORM_TYPE.SIEM.name()))
        .persist();
    // create exercise cover all TTPs
    ExerciseComposer.Composer exerciseWrapper =
        createExerciseWrapperWithInjectsForDomainObjects(Map.of(ap1, true, ap2, true), Map.of());
    exerciseWrapper.get().setStart(simulationStartTime);
    exerciseWrapper.get().setStatus(ExerciseStatus.FINISHED);

    // expectation results
    Inject successfulInject =
        injectComposer.generatedItems.stream()
            .filter(
                i ->
                    i.getInjectorContract().get().getAttackPatterns().stream()
                        .anyMatch(ap -> ap.getExternalId().equals("T1234")))
            .findFirst()
            .get();
    successfulInject
        .getExpectations()
        .forEach(
            exp ->
                exp.setResults(
                    List.of(
                        InjectExpectationResult.builder()
                            .score(100.0)
                            .sourceId(securityPlatformWrapper.get().getId())
                            .sourceName("Unit Tests")
                            .sourceType("manual")
                            .sourcePlatform(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name())
                            .sourceAssetId(UUID.randomUUID().toString())
                            .build())));

    Inject failedInject =
        injectComposer.generatedItems.stream()
            .filter(
                i ->
                    i.getInjectorContract().get().getAttackPatterns().stream()
                        .anyMatch(ap -> ap.getExternalId().equals("T5678")))
            .findFirst()
            .get();
    failedInject
        .getExpectations()
        .forEach(
            exp -> {
              exp.setResults(
                  List.of(
                      InjectExpectationResult.builder()
                          .score(0.0)
                          .sourceId(securityPlatformWrapper.get().getId())
                          .sourceName("Unit Tests")
                          .sourceType("manual")
                          .sourcePlatform(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name())
                          .sourceAssetId(UUID.randomUUID().toString())
                          .build()));
              exp.setScore(0.0);
            });

    ScenarioComposer.Composer scenarioWrapper =
        scenarioComposer.forScenario(ScenarioFixture.createDefaultCrisisScenario());
    scenarioWrapper.get().setRecurrence("P1D");
    scenarioWrapper.get().setRecurrenceStart(simulationStartTime);
    scenarioWrapper.get().setRecurrenceEnd(simulationStartTime.plus(30, ChronoUnit.DAYS));
    scenarioWrapper.withSimulation(exerciseWrapper).persist();

    entityManager.flush();
    entityManager.refresh(exerciseWrapper.get());

    Optional<SecurityCoverageSendJob> job =
        securityCoverageSendJobService.createOrUpdateCoverageSendJobForSimulationIfReady(
            exerciseWrapper.get());

    // intermediate assert
    assertThat(job).isNotEmpty();

    // act
    Bundle bundle = securityCoverageService.createBundleFromSendJobs(List.of(job.get()));

    // assert
    SecurityCoverage generatedCoverage = securityCoverageComposer.generatedItems.getFirst();
    List<Inject> generatedInjects = injectComposer.generatedItems;

    DomainObject expectedAssessmentWithCoverage =
        addPropertiesToDomainObject(
            getExpectedMainSecurityCoverage(generatedCoverage, generatedInjects),
            Map.of(
                ExtendedProperties.VALID_FROM.toString(), new Timestamp(simulationStartTime),
                ExtendedProperties.LAST_RESULT.toString(), new Timestamp(simulationStartTime),
                ExtendedProperties.VALID_TO.toString(), new Timestamp(nextSimulationStartTime)));

    // main assessment is completed with coverage
    assertThatJson(
            bundle.findById(new Identifier(generatedCoverage.getExternalId())).toStix(mapper))
        .whenIgnoringPaths(
            CommonProperties.MODIFIED.toString(),
            CommonProperties.EXTERNAL_URI.toString(),
            CommonProperties.TENANT_ID.toString(),
            CommonProperties.TENANT_NAME.toString(),
            CommonProperties.AUTO_ENRICHMENT_DISABLE.toString())
        .isEqualTo(expectedAssessmentWithCoverage.toStix(mapper));

    List<DomainObject> expectedPlatformIdentities = getExpectedPlatformIdentities();

    // security platforms are present in bundle as Identities
    for (DomainObject platformSdo : expectedPlatformIdentities) {
      assertThatJson(bundle.findById(platformSdo.getId()).toStix(mapper))
          .isEqualTo(platformSdo.toStix(mapper));

      // security platform SROs
      List<RelationshipObject> actualSros =
          bundle.findRelationshipsByTargetRef(platformSdo.getId());
      assertThat(actualSros.size()).isEqualTo(1);

      RelationshipObject actualSro = actualSros.getFirst();
      RelationshipObject expectedSro =
          new RelationshipObject(
              Map.of(
                  CommonProperties.ID.toString(),
                  new Identifier(ObjectTypes.RELATIONSHIP.toString(), UUID.randomUUID().toString()),
                  CommonProperties.TYPE.toString(),
                  new StixString(ObjectTypes.RELATIONSHIP.toString()),
                  RelationshipObject.Properties.RELATIONSHIP_TYPE.toString(),
                  new StixString("has-covered"),
                  RelationshipObject.Properties.SOURCE_REF.toString(),
                  expectedAssessmentWithCoverage.getId(),
                  RelationshipObject.Properties.TARGET_REF.toString(),
                  platformSdo.getId(),
                  RelationshipObject.Properties.START_TIME.toString(),
                  new Timestamp(simulationStartTime),
                  RelationshipObject.Properties.STOP_TIME.toString(),
                  new Timestamp(nextSimulationStartTime),
                  ExtendedProperties.COVERED.toString(),
                  new io.openaev.stix.types.Boolean(true),
                  CommonProperties.EXTERNAL_URI.toString(),
                  new StixString(
                      openAEVConfig.getBaseUrl()
                          + "/"
                          + TenantContext.getCurrentTenant()
                          + "/admin/scenarios/"
                          + scenarioWrapper.get().getId()),
                  ExtendedProperties.COVERAGE.toString(),
                  toList(
                      List.of(
                          new Complex<>(
                              new CoverageResult(
                                  "PREVENTION",
                                  platformSdo
                                          .getId()
                                          .getValue()
                                          .contains(securityPlatformWrapper.get().getId())
                                      ? 50
                                      : 0)),
                          new Complex<>(
                              new CoverageResult(
                                  "DETECTION",
                                  platformSdo
                                          .getId()
                                          .getValue()
                                          .contains(securityPlatformWrapper.get().getId())
                                      ? 50
                                      : 0))))));
      assertThatJson(actualSro.toStix(mapper))
          .whenIgnoringPaths(
              CommonProperties.ID.toString(), CommonProperties.EXTERNAL_URI.toString())
          .isEqualTo(expectedSro.toStix(mapper));
    }

    // Attack Pattern SROs
    for (StixRefToExternalRef stixRef : generatedCoverage.getAttackPatternRefs()) {
      List<RelationshipObject> actualSros =
          bundle.findRelationshipsByTargetRef(new Identifier(stixRef.getStixRef()));
      assertThat(actualSros.size()).isEqualTo(1);

      RelationshipObject actualSro = actualSros.getFirst();
      RelationshipObject expectedSro =
          new RelationshipObject(
              Map.of(
                  CommonProperties.ID.toString(),
                  new Identifier(ObjectTypes.RELATIONSHIP.toString(), UUID.randomUUID().toString()),
                  CommonProperties.TYPE.toString(),
                  new StixString(ObjectTypes.RELATIONSHIP.toString()),
                  RelationshipObject.Properties.RELATIONSHIP_TYPE.toString(),
                  new StixString("has-covered"),
                  RelationshipObject.Properties.SOURCE_REF.toString(),
                  expectedAssessmentWithCoverage.getId(),
                  RelationshipObject.Properties.TARGET_REF.toString(),
                  new Identifier(stixRef.getStixRef()),
                  RelationshipObject.Properties.START_TIME.toString(),
                  new Timestamp(simulationStartTime),
                  RelationshipObject.Properties.STOP_TIME.toString(),
                  new Timestamp(nextSimulationStartTime),
                  ExtendedProperties.COVERED.toString(),
                  new io.openaev.stix.types.Boolean(true),
                  CommonProperties.EXTERNAL_URI.toString(),
                  new StixString(
                      openAEVConfig.getBaseUrl()
                          + "/"
                          + TenantContext.getCurrentTenant()
                          + "/admin/scenarios/"
                          + scenarioWrapper.get().getId()),
                  ExtendedProperties.COVERAGE.toString(),
                  toList(
                      List.of(
                          new Complex<>(
                              new CoverageResult(
                                  "PREVENTION",
                                  stixRef.getExternalRefs().contains("T1234") ? 100 : 0)),
                          new Complex<>(
                              new CoverageResult(
                                  "DETECTION",
                                  stixRef.getExternalRefs().contains("T1234") ? 100 : 0))))));
      assertThatJson(actualSro.toStix(mapper))
          .whenIgnoringPaths(
              CommonProperties.ID.toString(), CommonProperties.EXTERNAL_URI.toString())
          .isEqualTo(expectedSro.toStix(mapper));
    }
  }

  @Test
  @DisplayName(
      "When there is a following simulation, set SRO stop time to following simulation start, not next scheduled simulation")
  public void
      whenThereIsAFollowingSimulation_setSROStopTimeToFollowingSimulationStartNotNextScheduledSimulation()
          throws ParsingException, JsonProcessingException {
    AttackPatternComposer.Composer ap1 =
        attackPatternComposer.forAttackPattern(
            AttackPatternFixture.createAttackPatternsWithExternalId("T1234"));
    SecurityPlatformComposer.Composer securityPlatformWrapper =
        securityPlatformComposer
            .forSecurityPlatform(
                SecurityPlatformFixture.createDefault(
                    "Bad EDR", SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name()))
            .persist();
    // create exercise cover all TTPs
    ExerciseComposer.Composer exerciseWrapper =
        createExerciseWrapperWithInjectsForDomainObjects(Map.of(ap1, true), Map.of());
    exerciseWrapper.get().setStatus(ExerciseStatus.FINISHED);

    // set SUCCESS results for all inject expectations
    Inject successfulInject =
        injectComposer.generatedItems.stream()
            .filter(
                i ->
                    i.getInjectorContract().get().getAttackPatterns().stream()
                        .anyMatch(ap -> ap.getExternalId().equals("T1234")))
            .findFirst()
            .get();
    successfulInject
        .getExpectations()
        .forEach(
            exp ->
                exp.setResults(
                    List.of(
                        InjectExpectationResult.builder()
                            .score(100.0)
                            .sourceId(securityPlatformWrapper.get().getId())
                            .sourceName("Unit Tests")
                            .sourceType("manual")
                            .sourcePlatform(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name())
                            .sourceAssetId(UUID.randomUUID().toString())
                            .build())));
    // start the exercise
    Instant sroStartTime = Instant.parse("2003-02-15T09:45:02Z");
    exerciseWrapper.get().setStart(sroStartTime);

    // persist
    ScenarioComposer.Composer scenarioWrapper =
        scenarioComposer
            .forScenario(ScenarioFixture.getScenarioWithRecurrence("0 0 16 * * *"))
            .withSimulation(
                exerciseWrapper.withSecurityCoverageSendJob(
                    securityCoverageSendJobComposer.forSecurityCoverageSendJob(
                        SecurityCoverageSendJobFixture.createDefaultSecurityCoverageSendJob())))
            .persist();
    entityManager.flush();
    entityManager.refresh(exerciseWrapper.get());

    // persist other simulation of same scenario
    Instant sroStopTime = Instant.parse("2004-06-26T12:34:56Z");
    Exercise newExercise = ExerciseFixture.createDefaultExercise();
    newExercise.setStart(sroStopTime);
    scenarioWrapper.withSimulation(exerciseComposer.forExercise(newExercise)).persist();
    entityManager.flush();
    entityManager.refresh(newExercise);

    // act
    Bundle bundle =
        securityCoverageService.createBundleFromSendJobs(
            securityCoverageSendJobComposer.generatedItems);

    // assert
    for (RelationshipObject sro : bundle.getRelationshipObjects()) {
      assertThat(sro.getProperty(RelationshipObject.Properties.START_TIME.toString()))
          .isEqualTo(new Timestamp(sroStartTime));
      assertThat(sro.getProperty(RelationshipObject.Properties.STOP_TIME.toString()))
          .isEqualTo(new Timestamp(sroStopTime));
    }
  }

  @Test
  @DisplayName("When no following simulation, set SRO stop time to next scheduled simulation start")
  public void whenThereIsAFollowingSimulation_setSROStopTimeToNextScheduledSimulationStart()
      throws ParsingException, JsonProcessingException {
    AttackPatternComposer.Composer ap1 =
        attackPatternComposer.forAttackPattern(
            AttackPatternFixture.createAttackPatternsWithExternalId("T1234"));
    SecurityPlatformComposer.Composer securityPlatformWrapper =
        securityPlatformComposer
            .forSecurityPlatform(
                SecurityPlatformFixture.createDefault(
                    "Bad EDR", SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name()))
            .persist();
    // create exercise cover all TTPs
    ExerciseComposer.Composer exerciseWrapper =
        createExerciseWrapperWithInjectsForDomainObjects(Map.of(ap1, true), Map.of());

    // set SUCCESS results for all inject expectations
    Inject successfulInject =
        injectComposer.generatedItems.stream()
            .filter(
                i ->
                    i.getInjectorContract().get().getAttackPatterns().stream()
                        .anyMatch(ap -> ap.getExternalId().equals("T1234")))
            .findFirst()
            .get();
    successfulInject
        .getExpectations()
        .forEach(
            exp ->
                exp.setResults(
                    List.of(
                        InjectExpectationResult.builder()
                            .score(100.0)
                            .sourceId(securityPlatformWrapper.get().getId())
                            .sourceName("Unit Tests")
                            .sourceType("manual")
                            .sourcePlatform(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name())
                            .sourceAssetId(UUID.randomUUID().toString())
                            .build())));
    // start the exercise
    Instant sroStartTime = Instant.parse("2003-02-15T19:45:02Z");
    Instant sroStopTime = Instant.parse("2003-02-16T16:00:00Z");
    exerciseWrapper.get().setStart(sroStartTime);

    // persist
    scenarioComposer
        .forScenario(
            ScenarioFixture.getScenarioWithRecurrence(
                "0 0 16 * * *")) // scheduled every day @ 16:00 UTC
        .withSimulation(exerciseWrapper)
        .persist();
    entityManager.flush();

    entityManager.refresh(exerciseWrapper.get());
    Optional<SecurityCoverageSendJob> job =
        securityCoverageSendJobService.createOrUpdateCoverageSendJobForSimulationIfReady(
            exerciseWrapper.get());

    // intermediate assert
    assertThat(job).isNotEmpty();

    // act
    Bundle bundle = securityCoverageService.createBundleFromSendJobs(List.of(job.get()));

    // assert
    for (RelationshipObject sro : bundle.getRelationshipObjects()) {
      assertThat(sro.getProperty(RelationshipObject.Properties.START_TIME.toString()))
          .isEqualTo(new Timestamp(sroStartTime));
      assertThat(sro.getProperty(RelationshipObject.Properties.STOP_TIME.toString()))
          .isEqualTo(new Timestamp(sroStopTime));
    }
  }

  @Test
  @DisplayName("When scenario is deleted, simulation still able to produce stix bundle")
  public void whenScenarioIsDeleted_simulationStillAbleToProduceStixBundle()
      throws ParsingException, JsonProcessingException {
    AttackPatternComposer.Composer ap1 =
        attackPatternComposer.forAttackPattern(
            AttackPatternFixture.createAttackPatternsWithExternalId("T1234"));
    SecurityPlatformComposer.Composer securityPlatformWrapper =
        securityPlatformComposer
            .forSecurityPlatform(
                SecurityPlatformFixture.createDefault(
                    "Bad EDR", SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name()))
            .persist();
    // create exercise cover all TTPs
    ExerciseComposer.Composer exerciseWrapper =
        createExerciseWrapperWithInjectsForDomainObjects(Map.of(ap1, true), Map.of());

    // set SUCCESS results for all inject expectations
    Inject successfulInject =
        injectComposer.generatedItems.stream()
            .filter(
                i ->
                    i.getInjectorContract().get().getAttackPatterns().stream()
                        .anyMatch(ap -> ap.getExternalId().equals("T1234")))
            .findFirst()
            .get();
    successfulInject
        .getExpectations()
        .forEach(
            exp ->
                exp.setResults(
                    List.of(
                        InjectExpectationResult.builder()
                            .score(100.0)
                            .sourceId(securityPlatformWrapper.get().getId())
                            .sourceName("Unit Tests")
                            .sourceType("manual")
                            .sourcePlatform(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name())
                            .sourceAssetId(UUID.randomUUID().toString())
                            .build())));
    // start the exercise
    Instant sroStartTime = Instant.parse("2003-02-15T19:45:02Z");
    exerciseWrapper.get().setStart(sroStartTime);
    exerciseWrapper.get().setStatus(ExerciseStatus.FINISHED);

    // persist
    exerciseWrapper.persist();
    entityManager.flush();

    entityManager.refresh(exerciseWrapper.get());
    Optional<SecurityCoverageSendJob> job =
        securityCoverageSendJobService.createOrUpdateCoverageSendJobForSimulationIfReady(
            exerciseWrapper.get());

    // intermediate assert
    assertThat(job).isNotEmpty();

    // act
    Bundle bundle = securityCoverageService.createBundleFromSendJobs(List.of(job.get()));

    // assert
    for (RelationshipObject sro : bundle.getRelationshipObjects()) {
      assertThat(sro.getProperty(RelationshipObject.Properties.START_TIME.toString()))
          .isEqualTo(new Timestamp(sroStartTime));
      assertThat(sro.hasProperty(RelationshipObject.Properties.STOP_TIME.toString())).isFalse();
    }
  }

  @Test
  @DisplayName(
      "When a simulation inject has no content, DNS indicator coverage is still computed without failing")
  public void given_simulationWithContentlessInject_should_computeDnsIndicatorCoverage()
      throws ParsingException, JsonProcessingException {
    String hostname = "malicious.example.com";
    String indicatorStixRef = "indicator--%s".formatted(UUID.randomUUID());
    SecurityPlatformComposer.Composer securityPlatformWrapper =
        securityPlatformComposer
            .forSecurityPlatform(
                SecurityPlatformFixture.createDefault(
                    "Bad EDR", SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name()))
            .persist();

    // an inject resolving the hostname carried by the indicator
    InjectComposer.Composer dnsInjectWrapper =
        injectComposer
            .forInject(
                InjectFixture.createInjectWithPayloadArg(
                    Map.of(DYNAMIC_DNS_RESOLUTION_HOSTNAME_KEY, hostname)))
            .withInjectorContract(
                injectorContractComposer
                    .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
                    .withInjector(injectorFixture.getWellKnownOaevImplantInjector()))
            .withExpectation(
                injectExpectationComposer
                    .forExpectation(
                        InjectExpectationFixture.createExpectationWithTypeAndStatus(
                            BaseInjectExpectation.EXPECTATION_TYPE.DETECTION,
                            BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS))
                    .withEndpoint(endpointComposer.forEndpoint(EndpointFixture.createEndpoint())));

    // an inject without an injector contract keeps a null content once persisted
    InjectComposer.Composer contentlessInjectWrapper =
        injectComposer.forInject(InjectFixture.getDefaultInject());

    SecurityCoverageComposer.Composer securityCoverageWrapper =
        securityCoverageComposer.forSecurityCoverage(
            SecurityCoverageFixture.createDefaultSecurityCoverage());
    securityCoverageWrapper
        .get()
        .setIndicatorsRefs(
            new HashSet<>(
                Set.of(
                    new StixRefToExternalRef(
                        indicatorStixRef, new ArrayList<>(List.of(hostname))))));

    ExerciseComposer.Composer exerciseWrapper =
        exerciseComposer
            .forExercise(ExerciseFixture.createDefaultExercise())
            .withSecurityCoverage(securityCoverageWrapper)
            .withInject(dnsInjectWrapper)
            .withInject(contentlessInjectWrapper);
    exerciseWrapper.get().setStart(Instant.parse("2024-09-23T14:09:43Z"));
    exerciseWrapper.get().setStatus(ExerciseStatus.FINISHED);

    injectExpectationComposer.generatedItems.forEach(
        exp ->
            exp.setResults(
                List.of(
                    InjectExpectationResult.builder()
                        .score(100.0)
                        .sourceId(securityPlatformWrapper.get().getId())
                        .sourceName("Unit Tests")
                        .sourceType("manual")
                        .sourcePlatform(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name())
                        .sourceAssetId(UUID.randomUUID().toString())
                        .build())));

    scenarioComposer
        .forScenario(ScenarioFixture.createDefaultCrisisScenario())
        .withSimulation(exerciseWrapper)
        .persist();
    entityManager.flush();
    entityManager.refresh(exerciseWrapper.get());

    // intermediate assert: the simulation really does carry a content-less inject
    assertThat(contentlessInjectWrapper.get().getContent()).isNull();

    Optional<SecurityCoverageSendJob> job =
        securityCoverageSendJobService.createOrUpdateCoverageSendJobForSimulationIfReady(
            exerciseWrapper.get());
    assertThat(job).isNotEmpty();

    // act
    Bundle bundle = securityCoverageService.createBundleFromSendJobs(List.of(job.orElseThrow()));

    // assert the indicator is reported as covered by the DNS inject
    List<RelationshipObject> indicatorSros =
        bundle.findRelationshipsByTargetRef(new Identifier(indicatorStixRef));
    assertThat(indicatorSros).hasSize(1);

    RelationshipObject indicatorSro = indicatorSros.getFirst();
    assertThat(indicatorSro.getProperty(ExtendedProperties.COVERED.toString()))
        .isEqualTo(new io.openaev.stix.types.Boolean(true));
    assertThatJson(indicatorSro.getProperty(ExtendedProperties.COVERAGE.toString()).toStix(mapper))
        .isEqualTo(predictCoverageFromInjects(List.of(dnsInjectWrapper.get())).toStix(mapper));
  }

  @Nested
  @DisplayName("Per-platform attribution of covered objects (coverage_platforms)")
  class CoveragePlatforms {

    private AttackPatternComposer.Composer persistedAttackPattern(String externalId) {
      return attackPatternComposer
          .forAttackPattern(AttackPatternFixture.createAttackPatternsWithExternalId(externalId))
          .persist();
    }

    private SecurityPlatform persistedPlatform(
        String name, SecurityPlatform.SECURITY_PLATFORM_TYPE type) {
      return securityPlatformComposer
          .forSecurityPlatform(SecurityPlatformFixture.createDefault(name, type.name()))
          .persist()
          .get();
    }

    /**
     * An inject covering the attack patterns, with one detection and one prevention expectation.
     */
    private InjectComposer.Composer injectCovering(
        AttackPatternComposer.Composer... attackPatterns) {
      InjectorContractComposer.Composer contract =
          injectorContractComposer
              .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
              .withInjector(injectorFixture.getWellKnownOaevImplantInjector());
      for (AttackPatternComposer.Composer attackPattern : attackPatterns) {
        contract.withAttackPattern(attackPattern);
      }
      return injectComposer
          .forInject(InjectFixture.getDefaultInject())
          .withInjectorContract(contract)
          .withExpectation(
              injectExpectationComposer
                  .forExpectation(
                      InjectExpectationFixture.createExpectationWithTypeAndStatus(
                          BaseInjectExpectation.EXPECTATION_TYPE.DETECTION,
                          BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS))
                  .withEndpoint(endpointComposer.forEndpoint(EndpointFixture.createEndpoint())))
          .withExpectation(
              injectExpectationComposer
                  .forExpectation(
                      InjectExpectationFixture.createExpectationWithTypeAndStatus(
                          BaseInjectExpectation.EXPECTATION_TYPE.PREVENTION,
                          BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS))
                  .withEndpoint(endpointComposer.forEndpoint(EndpointFixture.createEndpoint())));
    }

    private ExerciseComposer.Composer finishedSimulationCovering(
        List<AttackPatternComposer.Composer> attackPatterns, InjectComposer.Composer... injects) {
      ExerciseComposer.Composer exerciseWrapper =
          exerciseComposer
              .forExercise(ExerciseFixture.createDefaultExercise())
              .withSecurityCoverage(
                  securityCoverageComposer.forSecurityCoverage(
                      SecurityCoverageFixture.createSecurityCoverageWithDomainObjects(
                          attackPatterns.stream().map(AttackPatternComposer.Composer::get).toList(),
                          List.of())));
      for (InjectComposer.Composer inject : injects) {
        exerciseWrapper.withInject(inject);
      }
      exerciseWrapper.get().setStart(Instant.parse("2024-09-23T14:09:43Z"));
      exerciseWrapper.get().setStatus(ExerciseStatus.FINISHED);
      return exerciseWrapper;
    }

    /** Sets the results of the inject's expectation of the given type, and its global score. */
    private void answer(
        InjectComposer.Composer inject,
        BaseInjectExpectation.EXPECTATION_TYPE type,
        Double globalScore,
        InjectExpectationResult... results) {
      inject.get().getExpectations().stream()
          .filter(expectation -> expectation.getType() == type)
          .forEach(
              expectation -> {
                expectation.setResults(new ArrayList<>(List.of(results)));
                expectation.setScore(globalScore);
              });
    }

    private Bundle buildBundle(ExerciseComposer.Composer exerciseWrapper)
        throws ParsingException, JsonProcessingException {
      scenarioComposer
          .forScenario(ScenarioFixture.createDefaultCrisisScenario())
          .withSimulation(exerciseWrapper)
          .persist();
      entityManager.flush();
      entityManager.refresh(exerciseWrapper.get());
      Optional<SecurityCoverageSendJob> job =
          securityCoverageSendJobService.createOrUpdateCoverageSendJobForSimulationIfReady(
              exerciseWrapper.get());
      assertThat(job).isNotEmpty();
      return securityCoverageService.createBundleFromSendJobs(List.of(job.orElseThrow()));
    }

    private RelationshipObject coveredObjectSro(Bundle bundle, String attackPatternExternalId) {
      StixRefToExternalRef ref =
          securityCoverageComposer.generatedItems.getFirst().getAttackPatternRefs().stream()
              .filter(stixRef -> stixRef.getExternalRefs().contains(attackPatternExternalId))
              .findFirst()
              .orElseThrow();
      List<RelationshipObject> sros =
          bundle.findRelationshipsByTargetRef(new Identifier(ref.getStixRef()));
      assertThat(sros).hasSize(1);
      return sros.getFirst();
    }

    private void assertCoveragePlatforms(
        Bundle bundle, RelationshipObject sro, PlatformCoverageResult... expected)
        throws ParsingException {
      assertThatJson(
              sro.getProperty(ExtendedProperties.COVERAGE_PLATFORMS.toString()).toStix(mapper))
          .when(Option.IGNORING_ARRAY_ORDER)
          .isEqualTo(toList(Arrays.stream(expected).map(Complex::new).toList()).toStix(mapper));
      // every referenced platform is a security platform identity of the same bundle
      for (PlatformCoverageResult entry : expected) {
        ObjectBase identity = bundle.findById(new Identifier(entry.platformRef()));
        assertThat(identity.getProperty("identity_class"))
            .isEqualTo(new StixString("securityplatform"));
      }
    }

    private Identifier identityIdOf(SecurityPlatform platform) {
      return new Identifier(SecurityPlatform.stixIdentityId(platform.getName()));
    }

    private PlatformCoverageResult entry(SecurityPlatform platform, String name, int score) {
      return new PlatformCoverageResult(identityIdOf(platform).getValue(), name, score);
    }

    @Test
    @DisplayName("One platform: the covered object lists the results of that platform")
    void given_onePlatformResult_should_attributeTheCoveredObjectToThatPlatform()
        throws ParsingException, JsonProcessingException {
      // Arrange
      AttackPatternComposer.Composer technique = persistedAttackPattern("T9101");
      SecurityPlatform edr =
          persistedPlatform("Attribution EDR", SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR);
      InjectComposer.Composer inject = injectCovering(technique);
      ExerciseComposer.Composer simulation = finishedSimulationCovering(List.of(technique), inject);
      answer(
          inject,
          BaseInjectExpectation.EXPECTATION_TYPE.DETECTION,
          100.0,
          createCollectorResult(edr, 100.0));
      answer(
          inject,
          BaseInjectExpectation.EXPECTATION_TYPE.PREVENTION,
          100.0,
          createCollectorResult(edr, 100.0));

      // Act
      Bundle bundle = buildBundle(simulation);

      // Assert
      RelationshipObject sro = coveredObjectSro(bundle, "T9101");
      assertCoveragePlatforms(
          bundle, sro, entry(edr, "PREVENTION", 100), entry(edr, "DETECTION", 100));
      // the overall per-platform relationship attributes the collector results too
      RelationshipObject platformSro =
          bundle.findRelationshipsByTargetRef(identityIdOf(edr)).getFirst();
      assertThatJson(platformSro.getProperty(ExtendedProperties.COVERAGE.toString()).toStix(mapper))
          .isEqualTo(
              toList(
                      List.of(
                          new Complex<>(new CoverageResult("PREVENTION", 100)),
                          new Complex<>(new CoverageResult("DETECTION", 100))))
                  .toStix(mapper));
    }

    @Test
    @DisplayName("Several platforms: each platform is listed with its own scores")
    void given_severalPlatforms_should_listEachPlatformWithItsOwnScores()
        throws ParsingException, JsonProcessingException {
      // Arrange
      AttackPatternComposer.Composer technique = persistedAttackPattern("T9102");
      SecurityPlatform edr =
          persistedPlatform("Attribution EDR", SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR);
      SecurityPlatform siem =
          persistedPlatform("Attribution SIEM", SecurityPlatform.SECURITY_PLATFORM_TYPE.SIEM);
      InjectComposer.Composer inject = injectCovering(technique);
      ExerciseComposer.Composer simulation = finishedSimulationCovering(List.of(technique), inject);
      answer(
          inject,
          BaseInjectExpectation.EXPECTATION_TYPE.DETECTION,
          100.0,
          createCollectorResult(edr, 100.0),
          createCollectorResult(siem, 0.0));
      answer(
          inject,
          BaseInjectExpectation.EXPECTATION_TYPE.PREVENTION,
          100.0,
          createCollectorResult(edr, 100.0));

      // Act
      Bundle bundle = buildBundle(simulation);

      // Assert
      assertCoveragePlatforms(
          bundle,
          coveredObjectSro(bundle, "T9102"),
          entry(edr, "PREVENTION", 100),
          entry(edr, "DETECTION", 100),
          entry(siem, "DETECTION", 0));
    }

    /** An expectation of an agent of the endpoint, answered by the given collector results. */
    private InjectExpectationComposer.Composer agentExpectation(
        BaseInjectExpectation.EXPECTATION_TYPE type,
        AgentComposer.Composer agent,
        Double score,
        InjectExpectationResult... results) {
      BaseInjectExpectation expectation =
          InjectExpectationFixture.createExpectationWithTypeAndStatus(
              type, BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS);
      expectation.setResults(new ArrayList<>(List.of(results)));
      expectation.setScore(score);
      return injectExpectationComposer.forExpectation(expectation).withAgent(agent);
    }

    /**
     * The asset expectation of the endpoint: it only carries the score rolled up from its agents.
     */
    private InjectExpectationComposer.Composer assetExpectation(
        BaseInjectExpectation.EXPECTATION_TYPE type,
        EndpointComposer.Composer endpoint,
        Double score) {
      BaseInjectExpectation expectation =
          InjectExpectationFixture.createExpectationWithTypeAndStatus(
              type, BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS);
      expectation.setResults(new ArrayList<>());
      expectation.setScore(score);
      return injectExpectationComposer.forExpectation(expectation).withEndpoint(endpoint);
    }

    @Test
    @DisplayName(
        "Agent-backed collector results are attributed through the asset expectation they roll up to")
    void given_collectorResultsOnAgentExpectations_should_attributeThePlatformThroughTheAsset()
        throws ParsingException, JsonProcessingException {
      // Arrange
      AttackPatternComposer.Composer technique = persistedAttackPattern("T9108");
      SecurityPlatform edr =
          persistedPlatform("Agent-backed EDR", SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR);
      EndpointComposer.Composer endpoint =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint());
      AgentComposer.Composer firstAgent =
          agentComposer.forAgent(AgentFixture.createDefaultAgentService());
      AgentComposer.Composer secondAgent =
          agentComposer.forAgent(AgentFixture.createDefaultAgentService());
      endpoint.withAgent(firstAgent).withAgent(secondAgent);
      InjectorContractComposer.Composer contract =
          injectorContractComposer
              .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
              .withInjector(injectorFixture.getWellKnownOaevImplantInjector())
              .withAttackPattern(technique);
      // Collectors answer the agent expectations; the asset expectations only get the roll-up
      InjectComposer.Composer inject =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withInjectorContract(contract)
              .withEndpoint(endpoint)
              .withExpectation(
                  assetExpectation(
                      BaseInjectExpectation.EXPECTATION_TYPE.DETECTION, endpoint, 100.0))
              .withExpectation(
                  agentExpectation(
                      BaseInjectExpectation.EXPECTATION_TYPE.DETECTION,
                      firstAgent,
                      100.0,
                      createCollectorResult(edr, 100.0)))
              .withExpectation(
                  agentExpectation(
                      BaseInjectExpectation.EXPECTATION_TYPE.DETECTION,
                      secondAgent,
                      100.0,
                      createCollectorResult(edr, 100.0)))
              .withExpectation(
                  assetExpectation(
                      BaseInjectExpectation.EXPECTATION_TYPE.PREVENTION, endpoint, 0.0))
              .withExpectation(
                  agentExpectation(
                      BaseInjectExpectation.EXPECTATION_TYPE.PREVENTION,
                      firstAgent,
                      0.0,
                      createCollectorResult(edr, 0.0)))
              .withExpectation(
                  agentExpectation(
                      BaseInjectExpectation.EXPECTATION_TYPE.PREVENTION,
                      secondAgent,
                      0.0,
                      createCollectorResult(edr, 0.0)));
      ExerciseComposer.Composer simulation = finishedSimulationCovering(List.of(technique), inject);

      // Act
      Bundle bundle = buildBundle(simulation);

      // Assert
      RelationshipObject sro = coveredObjectSro(bundle, "T9108");
      assertCoveragePlatforms(
          bundle, sro, entry(edr, "PREVENTION", 0), entry(edr, "DETECTION", 100));
      RelationshipObject platformSro =
          bundle.findRelationshipsByTargetRef(identityIdOf(edr)).getFirst();
      assertThatJson(platformSro.getProperty(ExtendedProperties.COVERAGE.toString()).toStix(mapper))
          .when(Option.IGNORING_ARRAY_ORDER)
          .isEqualTo(
              toList(
                      List.of(
                          new Complex<>(new CoverageResult("PREVENTION", 0)),
                          new Complex<>(new CoverageResult("DETECTION", 100))))
                  .toStix(mapper));
    }

    @Test
    @DisplayName(
        "Inject with agent-level expectations only: the platform is attributed from the agent results")
    void given_injectWithAgentExpectationsOnly_should_attributeThePlatformFromTheAgentResults()
        throws ParsingException, JsonProcessingException {
      // Arrange
      AttackPatternComposer.Composer agentOnlyTechnique = persistedAttackPattern("T9109");
      AttackPatternComposer.Composer assetTechnique = persistedAttackPattern("T9110");
      SecurityPlatform edr =
          persistedPlatform("Agent-only EDR", SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR);
      EndpointComposer.Composer endpoint =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint());
      AgentComposer.Composer agent =
          agentComposer.forAgent(AgentFixture.createDefaultAgentService());
      endpoint.withAgent(agent);
      InjectorContractComposer.Composer contract =
          injectorContractComposer
              .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
              .withInjector(injectorFixture.getWellKnownOaevImplantInjector())
              .withAttackPattern(agentOnlyTechnique);
      // No asset expectation: the agent rows are the only rows of this inject
      InjectComposer.Composer agentOnlyInject =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withInjectorContract(contract)
              .withEndpoint(endpoint)
              .withExpectation(
                  agentExpectation(
                      BaseInjectExpectation.EXPECTATION_TYPE.DETECTION,
                      agent,
                      100.0,
                      createCollectorResult(edr, 100.0)))
              .withExpectation(
                  agentExpectation(
                      BaseInjectExpectation.EXPECTATION_TYPE.PREVENTION,
                      agent,
                      0.0,
                      createCollectorResult(edr, 0.0)));
      // An inject with primary expectations in the same simulation keeps its primary-only scoring
      InjectComposer.Composer assetInject = injectCovering(assetTechnique);
      answer(
          assetInject,
          BaseInjectExpectation.EXPECTATION_TYPE.DETECTION,
          100.0,
          createCollectorResult(edr, 100.0));
      answer(
          assetInject,
          BaseInjectExpectation.EXPECTATION_TYPE.PREVENTION,
          100.0,
          createCollectorResult(edr, 100.0));
      ExerciseComposer.Composer simulation =
          finishedSimulationCovering(
              List.of(agentOnlyTechnique, assetTechnique), agentOnlyInject, assetInject);

      // Act
      Bundle bundle = buildBundle(simulation);

      // Assert
      RelationshipObject agentOnlySro = coveredObjectSro(bundle, "T9109");
      assertCoveragePlatforms(
          bundle, agentOnlySro, entry(edr, "PREVENTION", 0), entry(edr, "DETECTION", 100));
      // covered and coverage are scored on the same agent results as coverage_platforms
      assertThat(agentOnlySro.getProperty(ExtendedProperties.COVERED.toString()))
          .isEqualTo(new io.openaev.stix.types.Boolean(true));
      assertThatJson(
              agentOnlySro.getProperty(ExtendedProperties.COVERAGE.toString()).toStix(mapper))
          .when(Option.IGNORING_ARRAY_ORDER)
          .isEqualTo(
              toList(
                      List.of(
                          new Complex<>(new CoverageResult("PREVENTION", 0)),
                          new Complex<>(new CoverageResult("DETECTION", 100))))
                  .toStix(mapper));
      RelationshipObject assetSro = coveredObjectSro(bundle, "T9110");
      assertCoveragePlatforms(
          bundle, assetSro, entry(edr, "PREVENTION", 100), entry(edr, "DETECTION", 100));
      assertThatJson(assetSro.getProperty(ExtendedProperties.COVERAGE.toString()).toStix(mapper))
          .isEqualTo(predictCoverageFromInjects(List.of(assetInject.get())).toStix(mapper));
      // the per-platform relationship scores both injects: 1 of 2 preventions, 2 of 2 detections
      RelationshipObject platformSro =
          bundle.findRelationshipsByTargetRef(identityIdOf(edr)).getFirst();
      assertThatJson(platformSro.getProperty(ExtendedProperties.COVERAGE.toString()).toStix(mapper))
          .when(Option.IGNORING_ARRAY_ORDER)
          .isEqualTo(
              toList(
                      List.of(
                          new Complex<>(new CoverageResult("PREVENTION", 50)),
                          new Complex<>(new CoverageResult("DETECTION", 100))))
                  .toStix(mapper));
    }

    @Test
    @DisplayName("No platform result: coverage_platforms is omitted, coverage is still computed")
    void given_noPlatformResult_should_omitCoveragePlatforms()
        throws ParsingException, JsonProcessingException {
      // Arrange
      AttackPatternComposer.Composer technique = persistedAttackPattern("T9103");
      InjectComposer.Composer inject = injectCovering(technique);
      ExerciseComposer.Composer simulation = finishedSimulationCovering(List.of(technique), inject);
      // a manual validation and a source asset that is no security platform: nothing attributable
      InjectExpectationResult notAPlatform = createManualResult(100.0);
      notAPlatform.setSourceAssetId(UUID.randomUUID().toString());
      answer(
          inject,
          BaseInjectExpectation.EXPECTATION_TYPE.DETECTION,
          100.0,
          createManualResult(100.0));
      answer(inject, BaseInjectExpectation.EXPECTATION_TYPE.PREVENTION, 100.0, notAPlatform);

      // Act
      Bundle bundle = buildBundle(simulation);

      // Assert
      RelationshipObject sro = coveredObjectSro(bundle, "T9103");
      assertThat(sro.hasProperty(ExtendedProperties.COVERAGE_PLATFORMS.toString())).isFalse();
      assertThat(sro.getProperty(ExtendedProperties.COVERED.toString()))
          .isEqualTo(new io.openaev.stix.types.Boolean(true));
      assertThatJson(sro.getProperty(ExtendedProperties.COVERAGE.toString()).toStix(mapper))
          .isEqualTo(predictCoverageFromInjects(List.of(inject.get())).toStix(mapper));
      assertThat(bundle.findByType(ObjectTypes.IDENTITY)).isEmpty();
    }

    @Test
    @DisplayName("Mixed results: detection and prevention are scored separately per platform")
    void given_mixedDetectionAndPreventionResults_should_scoreEachExpectationTypeSeparately()
        throws ParsingException, JsonProcessingException {
      // Arrange
      AttackPatternComposer.Composer technique = persistedAttackPattern("T9104");
      SecurityPlatform edr =
          persistedPlatform("Attribution EDR", SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR);
      InjectComposer.Composer inject = injectCovering(technique);
      ExerciseComposer.Composer simulation = finishedSimulationCovering(List.of(technique), inject);
      answer(
          inject,
          BaseInjectExpectation.EXPECTATION_TYPE.DETECTION,
          100.0,
          createCollectorResult(edr, 100.0));
      answer(
          inject,
          BaseInjectExpectation.EXPECTATION_TYPE.PREVENTION,
          0.0,
          createCollectorResult(edr, 0.0));

      // Act
      Bundle bundle = buildBundle(simulation);

      // Assert
      RelationshipObject sro = coveredObjectSro(bundle, "T9104");
      assertCoveragePlatforms(
          bundle, sro, entry(edr, "PREVENTION", 0), entry(edr, "DETECTION", 100));
      assertThatJson(sro.getProperty(ExtendedProperties.COVERAGE.toString()).toStix(mapper))
          .isEqualTo(
              toList(
                      List.of(
                          new Complex<>(new CoverageResult("PREVENTION", 0)),
                          new Complex<>(new CoverageResult("DETECTION", 100))))
                  .toStix(mapper));
    }

    @Test
    @DisplayName(
        "Technique covered by several injects: each platform is scored on its own injects only")
    void
        given_techniqueCoveredByInjectsWithDifferentPlatforms_should_attributeEachPlatformItsInjects()
            throws ParsingException, JsonProcessingException {
      // Arrange
      AttackPatternComposer.Composer sharedTechnique = persistedAttackPattern("T9105");
      AttackPatternComposer.Composer serverTechnique = persistedAttackPattern("T9106");
      SecurityPlatform workstationEdr =
          persistedPlatform("Workstation EDR", SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR);
      SecurityPlatform ndr =
          persistedPlatform("Attribution NDR", SecurityPlatform.SECURITY_PLATFORM_TYPE.NDR);
      InjectComposer.Composer workstationInject = injectCovering(sharedTechnique);
      InjectComposer.Composer serverInject = injectCovering(sharedTechnique, serverTechnique);
      ExerciseComposer.Composer simulation =
          finishedSimulationCovering(
              List.of(sharedTechnique, serverTechnique), workstationInject, serverInject);
      answer(
          workstationInject,
          BaseInjectExpectation.EXPECTATION_TYPE.DETECTION,
          100.0,
          createCollectorResult(workstationEdr, 100.0));
      answer(
          workstationInject,
          BaseInjectExpectation.EXPECTATION_TYPE.PREVENTION,
          100.0,
          createCollectorResult(workstationEdr, 100.0));
      answer(
          serverInject,
          BaseInjectExpectation.EXPECTATION_TYPE.DETECTION,
          0.0,
          createCollectorResult(ndr, 0.0));
      answer(serverInject, BaseInjectExpectation.EXPECTATION_TYPE.PREVENTION, 0.0);

      // Act
      Bundle bundle = buildBundle(simulation);

      // Assert: the shared technique attributes each platform only the inject it reported on
      RelationshipObject sharedSro = coveredObjectSro(bundle, "T9105");
      assertCoveragePlatforms(
          bundle,
          sharedSro,
          entry(workstationEdr, "PREVENTION", 100),
          entry(workstationEdr, "DETECTION", 100),
          entry(ndr, "DETECTION", 0));
      assertThatJson(sharedSro.getProperty(ExtendedProperties.COVERAGE.toString()).toStix(mapper))
          .isEqualTo(
              predictCoverageFromInjects(List.of(workstationInject.get(), serverInject.get()))
                  .toStix(mapper));
      // the server-only technique never sees the workstation platform
      assertCoveragePlatforms(
          bundle, coveredObjectSro(bundle, "T9106"), entry(ndr, "DETECTION", 0));
    }

    @Test
    @DisplayName("Existing has-covered properties are unchanged, coverage_platforms is only added")
    void given_platformResults_should_keepEveryExistingPropertyOfTheCoveredObject()
        throws ParsingException, JsonProcessingException {
      // Arrange
      AttackPatternComposer.Composer technique = persistedAttackPattern("T9107");
      SecurityPlatform edr =
          persistedPlatform("Attribution EDR", SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR);
      InjectComposer.Composer inject = injectCovering(technique);
      ExerciseComposer.Composer simulation = finishedSimulationCovering(List.of(technique), inject);
      answer(
          inject,
          BaseInjectExpectation.EXPECTATION_TYPE.DETECTION,
          100.0,
          createCollectorResult(edr, 100.0));
      answer(
          inject,
          BaseInjectExpectation.EXPECTATION_TYPE.PREVENTION,
          0.0,
          createCollectorResult(edr, 0.0));

      // Act
      Bundle bundle = buildBundle(simulation);

      // Assert
      SecurityCoverage generatedCoverage = securityCoverageComposer.generatedItems.getFirst();
      StixRefToExternalRef ref = generatedCoverage.getAttackPatternRefs().iterator().next();
      RelationshipObject sro = coveredObjectSro(bundle, "T9107");
      RelationshipObject expectedLegacySro =
          new RelationshipObject(
              Map.of(
                  CommonProperties.ID.toString(),
                  new Identifier(ObjectTypes.RELATIONSHIP.toString(), UUID.randomUUID().toString()),
                  CommonProperties.TYPE.toString(),
                  new StixString(ObjectTypes.RELATIONSHIP.toString()),
                  RelationshipObject.Properties.RELATIONSHIP_TYPE.toString(),
                  new StixString("has-covered"),
                  RelationshipObject.Properties.SOURCE_REF.toString(),
                  new Identifier(generatedCoverage.getExternalId()),
                  RelationshipObject.Properties.TARGET_REF.toString(),
                  new Identifier(ref.getStixRef()),
                  RelationshipObject.Properties.START_TIME.toString(),
                  new Timestamp(Instant.parse("2024-09-23T14:09:43Z")),
                  ExtendedProperties.COVERED.toString(),
                  new io.openaev.stix.types.Boolean(true),
                  ExtendedProperties.COVERAGE.toString(),
                  predictCoverageFromInjects(List.of(inject.get()))));
      assertThatJson(sro.toStix(mapper))
          .whenIgnoringPaths(
              CommonProperties.ID.toString(),
              CommonProperties.EXTERNAL_URI.toString(),
              RelationshipObject.Properties.STOP_TIME.toString(),
              ExtendedProperties.COVERAGE_PLATFORMS.toString())
          .isEqualTo(expectedLegacySro.toStix(mapper));
      assertThat(sro.hasProperty(ExtendedProperties.COVERAGE_PLATFORMS.toString())).isTrue();
      // the main coverage object is untouched
      assertThat(
              bundle
                  .findById(new Identifier(generatedCoverage.getExternalId()))
                  .hasProperty(ExtendedProperties.COVERAGE_PLATFORMS.toString()))
          .isFalse();
    }
  }

  private List<DomainObject> getExpectedPlatformIdentities() {
    Set<String> involvedPlatformNames =
        injectComposer.generatedItems.stream()
            .flatMap(inject -> inject.getExpectations().stream())
            .flatMap(exp -> exp.getResults().stream())
            .map(InjectExpectationResult::getSourceName)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    return securityPlatformComposer.generatedItems.stream()
        .filter(sp -> involvedPlatformNames.contains(sp.getName()))
        .map(SecurityPlatform::toStixDomainObject)
        .toList();
  }
}
