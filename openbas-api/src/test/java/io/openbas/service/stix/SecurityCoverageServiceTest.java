package io.openbas.service.stix;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openbas.IntegrationTest;
import io.openbas.database.model.*;
import io.openbas.service.SecurityCoverageSendJobService;
import io.openbas.stix.objects.Bundle;
import io.openbas.stix.objects.DomainObject;
import io.openbas.stix.objects.RelationshipObject;
import io.openbas.stix.objects.constants.CommonProperties;
import io.openbas.stix.objects.constants.ExtendedProperties;
import io.openbas.stix.objects.constants.ObjectTypes;
import io.openbas.stix.parsing.Parser;
import io.openbas.stix.parsing.ParsingException;
import io.openbas.stix.types.*;
import io.openbas.stix.types.Dictionary;
import io.openbas.utils.InjectExpectationResultUtils;
import io.openbas.utils.ResultUtils;
import io.openbas.utils.fixtures.*;
import io.openbas.utils.fixtures.composers.*;
import io.openbas.utils.fixtures.files.AttackPatternFixture;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class SecurityCoverageServiceTest extends IntegrationTest {

  @Autowired private SecurityCoverageService securityCoverageService;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectExpectationComposer injectExpectationComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private SecurityCoverageComposer securityCoverageComposer;
  @Autowired private SecurityCoverageSendJobComposer securityCoverageSendJobComposer;
  @Autowired private InjectorFixture injectorFixture;
  @Autowired private AttackPatternComposer attackPatternComposer;
  @Autowired private CveComposer vulnerabilityComposer;
  @Autowired private SecurityPlatformComposer securityPlatformComposer;
  @Autowired private EntityManager entityManager;
  @Autowired private SecurityCoverageSendJobService securityCoverageSendJobService;
  @Autowired private ObjectMapper mapper;
  @Autowired private ResultUtils resultUtils;
  private Parser stixParser;

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
      Map<CveComposer.Composer, java.lang.Boolean> vulnWrappers) {

    // ensure attack patterns have IDs
    attackPatternWrappers.keySet().forEach(AttackPatternComposer.Composer::persist);
    // ensure vulns have IDs
    vulnWrappers.keySet().forEach(CveComposer.Composer::persist);

    List<AttackPattern> attackPatternList =
        attackPatternWrappers.keySet().stream().map(AttackPatternComposer.Composer::get).toList();
    List<Cve> vulnerabilities =
        vulnWrappers.keySet().stream().map(CveComposer.Composer::get).toList();

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
                        .withInjector(injectorFixture.getWellKnownObasImplantInjector())
                        .withAttackPattern(apw.getKey()))
                .withExpectation(
                    injectExpectationComposer
                        .forExpectation(
                            InjectExpectationFixture.createExpectationWithTypeAndStatus(
                                InjectExpectation.EXPECTATION_TYPE.DETECTION,
                                InjectExpectation.EXPECTATION_STATUS.SUCCESS))
                        .withEndpoint(
                            endpointComposer.forEndpoint(EndpointFixture.createEndpoint())))
                .withExpectation(
                    injectExpectationComposer
                        .forExpectation(
                            InjectExpectationFixture.createExpectationWithTypeAndStatus(
                                InjectExpectation.EXPECTATION_TYPE.PREVENTION,
                                InjectExpectation.EXPECTATION_STATUS.SUCCESS))
                        .withEndpoint(
                            endpointComposer.forEndpoint(EndpointFixture.createEndpoint()))));
      }
    }

    for (Map.Entry<CveComposer.Composer, java.lang.Boolean> vulnw : vulnWrappers.entrySet()) {
      if (vulnw.getValue()) { // this vuln should be covered
        exerciseWrapper.withInject(
            injectComposer
                .forInject(InjectFixture.getDefaultInject())
                .withInjectorContract(
                    injectorContractComposer
                        .forInjectorContract(
                            InjectorContractFixture.createDefaultInjectorContract())
                        .withInjector(injectorFixture.getWellKnownObasImplantInjector())
                        .withVulnerability(vulnw.getKey()))
                .withExpectation(
                    injectExpectationComposer
                        .forExpectation(
                            InjectExpectationFixture.createExpectationWithTypeAndStatus(
                                InjectExpectation.EXPECTATION_TYPE.VULNERABILITY,
                                InjectExpectation.EXPECTATION_STATUS.SUCCESS))
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

  private Dictionary predictCoverageFromInjects(List<Inject> injects) {
    List<InjectExpectationResultUtils.ExpectationResultsByType> results =
        resultUtils.computeGlobalExpectationResults(
            injects.stream().map(Inject::getId).collect(Collectors.toSet()));
    Map<String, BaseType<?>> dict = new HashMap<>();
    for (InjectExpectationResultUtils.ExpectationResultsByType result : results) {
      dict.put(result.type().toString(), new StixString(String.valueOf(result.getSuccessRate())));
    }
    return toDictionary(dict);
  }

  private Dictionary toDictionary(Map<String, BaseType<?>> map) {
    return new Dictionary(map);
  }

  private DomainObject getExpectedMainSecurityCoverage(
      SecurityCoverage securityCoverage, List<Inject> injects)
      throws ParsingException, JsonProcessingException {
    return addPropertiesToDomainObject(
        (DomainObject) stixParser.parseObject(securityCoverage.getContent()),
        Map.of(ExtendedProperties.COVERAGE.toString(), predictCoverageFromInjects(injects)));
  }

  private List<DomainObject> getExpectedPlatformIdentities(
      List<SecurityPlatform> securityPlatforms) {
    return securityPlatforms.stream().map(SecurityPlatform::toStixDomainObject).toList();
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
        throws JsonProcessingException, ParsingException {

      assertThatJson(
              bundle.findById(new Identifier(generatedCoverage.getExternalId())).toStix(mapper))
          .whenIgnoringPaths(CommonProperties.MODIFIED.toString())
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
              .forSecurityPlatform(SecurityPlatformFixture.createDefaultEDR())
              .persist();
      // another nameless platform not involved in simulation
      securityPlatformComposer
          .forSecurityPlatform(SecurityPlatformFixture.createDefaultEDR())
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
      Bundle bundle = securityCoverageService.createBundleFromSendJobs(List.of(job.get()));

      // assert
      SecurityCoverage generatedCoverage = securityCoverageComposer.generatedItems.getFirst();
      SecurityCoverage coverage = securityCoverageComposer.generatedItems.getFirst();
      DomainObject expectedAssessmentWithCoverage =
          getExpectedMainSecurityCoverage(coverage, injectComposer.generatedItems);
      List<DomainObject> expectedPlatformIdentities =
          securityPlatformComposer.generatedItems.stream()
              .map(SecurityPlatform::toStixDomainObject)
              .toList();

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
                    new StixString("has-assessed"),
                    RelationshipObject.Properties.SOURCE_REF.toString(),
                    expectedAssessmentWithCoverage.getId(),
                    RelationshipObject.Properties.TARGET_REF.toString(),
                    platformSdo.getId(),
                    ExtendedProperties.COVERED.toString(),
                    new io.openbas.stix.types.Boolean(true),
                    ExtendedProperties.COVERAGE.toString(),
                    toDictionary(
                        Map.of(
                            "PREVENTION",
                            new StixString(
                                platformSdo
                                        .getId()
                                        .getValue()
                                        .contains(securityPlatformWrapper.get().getId())
                                    ? "1.0"
                                    : "0.0"),
                            "DETECTION",
                            new StixString(
                                platformSdo
                                        .getId()
                                        .getValue()
                                        .contains(securityPlatformWrapper.get().getId())
                                    ? "1.0"
                                    : "0.0")))));
        assertThatJson(actualSro.toStix(mapper))
            .whenIgnoringPaths(CommonProperties.ID.toString())
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
                    new StixString("has-assessed"),
                    RelationshipObject.Properties.SOURCE_REF.toString(),
                    expectedAssessmentWithCoverage.getId(),
                    RelationshipObject.Properties.TARGET_REF.toString(),
                    new Identifier(stixRef.getStixRef()),
                    ExtendedProperties.COVERED.toString(),
                    new io.openbas.stix.types.Boolean(true),
                    ExtendedProperties.COVERAGE.toString(),
                    toDictionary(
                        Map.of(
                            "PREVENTION",
                            new StixString("1.0"),
                            "DETECTION",
                            new StixString("1.0")))));
        assertThatJson(actualSro.toStix(mapper))
            .whenIgnoringPaths(CommonProperties.ID.toString())
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
        CveComposer.Composer vuln1 =
            vulnerabilityComposer.forCve(CveFixture.createDefaultCve("CVE-1234-5678"));
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
                      new StixString("has-assessed"),
                      RelationshipObject.Properties.SOURCE_REF.toString(),
                      expectedAssessmentWithCoverage.getId(),
                      RelationshipObject.Properties.TARGET_REF.toString(),
                      new Identifier(stixRef.getStixRef()),
                      ExtendedProperties.COVERED.toString(),
                      new io.openbas.stix.types.Boolean(true),
                      ExtendedProperties.COVERAGE.toString(),
                      toDictionary(Map.of("VULNERABILITY", new StixString("1.0")))));
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
        CveComposer.Composer vuln1 =
            vulnerabilityComposer.forCve(CveFixture.createDefaultCve("CVE-1234-5678"));
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
    }
  }

  @Test
  @DisplayName(
      "When all attack patterns are covered and half of expectations are successful, bundle is correct")
  public void whenAllAttackPatternsAreCoveredAndHalfOfAllExpectationsAreSuccessful_bundleIsCorrect()
      throws ParsingException, JsonProcessingException {
    AttackPatternComposer.Composer ap1 =
        attackPatternComposer.forAttackPattern(
            AttackPatternFixture.createAttackPatternsWithExternalId("T1234"));
    AttackPatternComposer.Composer ap2 =
        attackPatternComposer.forAttackPattern(
            AttackPatternFixture.createAttackPatternsWithExternalId("T5678"));
    SecurityPlatformComposer.Composer securityPlatformWrapper =
        securityPlatformComposer
            .forSecurityPlatform(SecurityPlatformFixture.createDefaultEDR())
            .persist();
    // another nameless platform not involved in simulation
    securityPlatformComposer
        .forSecurityPlatform(SecurityPlatformFixture.createDefaultEDR())
        .persist();
    // create exercise cover all TTPs
    ExerciseComposer.Composer exerciseWrapper =
        createExerciseWrapperWithInjectsForDomainObjects(Map.of(ap1, true, ap2, true), Map.of());
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
                          .build()));
              exp.setScore(0.0);
            });

    scenarioComposer
        .forScenario(ScenarioFixture.createDefaultCrisisScenario())
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
    SecurityCoverage generatedCoverage = securityCoverageComposer.generatedItems.getFirst();
    List<Inject> generatedInjects = injectComposer.generatedItems;
    List<SecurityPlatform> generatedSecurityPlatforms = securityPlatformComposer.generatedItems;
    List<AttackPattern> generatedAttackPatterns = attackPatternComposer.generatedItems;

    DomainObject expectedAssessmentWithCoverage =
        getExpectedMainSecurityCoverage(generatedCoverage, generatedInjects);
    List<DomainObject> expectedPlatformIdentities =
        generatedSecurityPlatforms.stream().map(SecurityPlatform::toStixDomainObject).toList();

    // main assessment is completed with coverage
    assertThatJson(
            bundle.findById(new Identifier(generatedCoverage.getExternalId())).toStix(mapper))
        .whenIgnoringPaths(CommonProperties.MODIFIED.toString())
        .isEqualTo(expectedAssessmentWithCoverage.toStix(mapper));

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
                  new StixString("has-assessed"),
                  RelationshipObject.Properties.SOURCE_REF.toString(),
                  expectedAssessmentWithCoverage.getId(),
                  RelationshipObject.Properties.TARGET_REF.toString(),
                  platformSdo.getId(),
                  ExtendedProperties.COVERED.toString(),
                  new io.openbas.stix.types.Boolean(true),
                  ExtendedProperties.COVERAGE.toString(),
                  toDictionary(
                      Map.of(
                          "PREVENTION",
                          new StixString(
                              platformSdo
                                      .getId()
                                      .getValue()
                                      .contains(securityPlatformWrapper.get().getId())
                                  ? "0.5"
                                  : "0.0"),
                          "DETECTION",
                          new StixString(
                              platformSdo
                                      .getId()
                                      .getValue()
                                      .contains(securityPlatformWrapper.get().getId())
                                  ? "0.5"
                                  : "0.0")))));
      assertThatJson(actualSro.toStix(mapper))
          .whenIgnoringPaths(CommonProperties.ID.toString())
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
                  new StixString("has-assessed"),
                  RelationshipObject.Properties.SOURCE_REF.toString(),
                  expectedAssessmentWithCoverage.getId(),
                  RelationshipObject.Properties.TARGET_REF.toString(),
                  new Identifier(stixRef.getStixRef()),
                  ExtendedProperties.COVERED.toString(),
                  new io.openbas.stix.types.Boolean(true),
                  ExtendedProperties.COVERAGE.toString(),
                  toDictionary(
                      Map.of(
                          "PREVENTION",
                          new StixString(stixRef.getExternalRef().equals("T1234") ? "1.0" : "0.0"),
                          "DETECTION",
                          new StixString(
                              stixRef.getExternalRef().equals("T1234") ? "1.0" : "0.0")))));
      assertThatJson(actualSro.toStix(mapper))
          .whenIgnoringPaths(CommonProperties.ID.toString())
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
            .forSecurityPlatform(SecurityPlatformFixture.createDefaultEDR())
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
            .forSecurityPlatform(SecurityPlatformFixture.createDefaultEDR())
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
            .forSecurityPlatform(SecurityPlatformFixture.createDefaultEDR())
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
                            .build())));
    // start the exercise
    Instant sroStartTime = Instant.parse("2003-02-15T19:45:02Z");
    Instant sroStopTime = Instant.parse("2003-02-16T16:00:00Z");
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
}
