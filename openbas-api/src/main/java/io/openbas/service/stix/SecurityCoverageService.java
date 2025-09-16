package io.openbas.service.stix;

import static io.openbas.utils.SecurityCoverageUtils.extractAndValidateCoverage;
import static io.openbas.utils.SecurityCoverageUtils.extractObjectReferences;
import static io.openbas.utils.constants.StixConstants.ATTACK_SCENARIO;
import static io.openbas.utils.constants.StixConstants.STIX_DESCRIPTION;
import static io.openbas.utils.constants.StixConstants.STIX_NAME;
import static io.openbas.utils.constants.StixConstants.STIX_PERIOD_END;
import static io.openbas.utils.constants.StixConstants.STIX_PERIOD_START;
import static io.openbas.utils.constants.StixConstants.STIX_SCHEDULING;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openbas.cron.ScheduleFrequency;
import io.openbas.database.model.*;
import io.openbas.database.repository.ScenarioRepository;
import io.openbas.database.repository.SecurityCoverageRepository;
import io.openbas.rest.attack_pattern.service.AttackPatternService;
import io.openbas.rest.exercise.service.ExerciseService;
import io.openbas.rest.tag.TagService;
import io.openbas.service.AssetService;
import io.openbas.service.ScenarioService;
import io.openbas.service.cron.CronService;
import io.openbas.stix.objects.Bundle;
import io.openbas.stix.objects.DomainObject;
import io.openbas.stix.objects.ObjectBase;
import io.openbas.stix.objects.RelationshipObject;
import io.openbas.stix.objects.constants.CommonProperties;
import io.openbas.stix.objects.constants.ExtendedProperties;
import io.openbas.stix.objects.constants.ObjectTypes;
import io.openbas.stix.parsing.Parser;
import io.openbas.stix.parsing.ParsingException;
import io.openbas.stix.types.BaseType;
import io.openbas.stix.types.Identifier;
import io.openbas.stix.types.StixString;
import io.openbas.stix.types.Timestamp;
import io.openbas.utils.InjectExpectationResultUtils;
import io.openbas.utils.ResultUtils;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class SecurityCoverageService {

  private final ScenarioService scenarioService;
  private final SecurityCoverageInjectService securityCoverageInjectService;
  private final TagService tagService;
  private final CronService cronService;
  private final AttackPatternService attackPatternService;
  private final ResultUtils resultUtils;
  private final ExerciseService exerciseService;
  private final AssetService assetService;

  private final ScenarioRepository scenarioRepository;
  private final SecurityCoverageRepository securityCoverageRepository;

  private final Parser stixParser;

  private final ObjectMapper objectMapper;

  /**
   * Builds and persists a {@link SecurityCoverage} from a provided STIX JSON string.
   *
   * <p>This method parses the input STIX content, extracts relevant fields, maps them to a {@link
   * SecurityCoverage} domain object, and saves it. It also extracts referenced attack patterns and
   * sets optional fields like description and scheduling.
   *
   * @param stixJson STIX-formatted JSON string representing a security coverage
   * @return the saved {@link SecurityCoverage} object
   * @throws IOException if the input cannot be parsed into JSON
   * @throws ParsingException if the STIX bundle is malformed
   */
  public SecurityCoverage buildSecurityCoverageFromStix(String stixJson)
      throws IOException, ParsingException {

    JsonNode root = objectMapper.readTree(stixJson);
    Bundle bundle = stixParser.parseBundle(root.toString());

    ObjectBase stixCoverageObj = extractAndValidateCoverage(bundle);

    // Mandatory fields
    String externalId = stixCoverageObj.getRequiredProperty(CommonProperties.ID.toString());
    SecurityCoverage securityCoverage = getByExternalIdOrCreateSecurityCoverage(externalId);
    securityCoverage.setExternalId(externalId);

    String name = stixCoverageObj.getRequiredProperty(STIX_NAME);
    securityCoverage.setName(name);

    // Optional fields
    stixCoverageObj.setIfPresent(STIX_DESCRIPTION, securityCoverage::setDescription);
    stixCoverageObj.setIfSetPresent(
        CommonProperties.LABELS.toString(), securityCoverage::setLabels);

    // Extract Attack Patterns
    securityCoverage.setAttackPatternRefs(
        extractObjectReferences(bundle.findByType(ObjectTypes.ATTACK_PATTERN.toString())));

    // Extract vulnerabilities
    securityCoverage.setVulnerabilitiesRefs(
        extractObjectReferences(bundle.findByType(ObjectTypes.VULNERABILITY.toString())));

    // Default Fields
    String scheduling =
        stixCoverageObj.getOptionalProperty(STIX_SCHEDULING, ScheduleFrequency.ONESHOT.toString());
    securityCoverage.setScheduling(ScheduleFrequency.fromString(scheduling));

    // Period Start & End
    stixCoverageObj.setInstantIfPresent(STIX_PERIOD_START, securityCoverage::setPeriodStart);
    stixCoverageObj.setInstantIfPresent(STIX_PERIOD_END, securityCoverage::setPeriodEnd);

    securityCoverage.setContent(stixCoverageObj.toStix(objectMapper).toString());
    return save(securityCoverage);
  }

  /**
   * Retrieves a {@link SecurityCoverage} by its external ID. If no existing coverage is found, a
   * new instance is returned.
   *
   * @param externalId the external identifier from the STIX content
   * @return an existing or new {@link SecurityCoverage}
   */
  public SecurityCoverage getByExternalIdOrCreateSecurityCoverage(String externalId) {
    return securityCoverageRepository.findByExternalId(externalId).orElseGet(SecurityCoverage::new);
  }

  /**
   * Persists {@link SecurityCoverage} to the repository.
   *
   * @param securityCoverage the security coverage to save
   * @return the saved {@link SecurityCoverage}
   */
  public SecurityCoverage save(SecurityCoverage securityCoverage) {
    return securityCoverageRepository.save(securityCoverage);
  }

  /**
   * Builds a {@link Scenario} object based on a given {@link SecurityCoverage}.
   *
   * <p>This will create or update the associated scenario and generate the appropriate injects by
   * delegating to the {@code securityCoverageInjectService}.
   *
   * @param securityCoverage the source coverage
   * @return the created or updated {@link Scenario}
   */
  public Scenario buildScenarioFromSecurityCoverage(SecurityCoverage securityCoverage) {
    Scenario scenario = updateOrCreateScenarioFromSecurityCoverage(securityCoverage);
    securityCoverage.setScenario(scenario);
    Set<Inject> injects =
        securityCoverageInjectService.createdInjectsForScenarioAndSecurityCoverage(
            scenario, securityCoverage);
    scenario.setInjects(injects);
    return scenario;
  }

  /**
   * Updates an existing {@link Scenario} from a {@link SecurityCoverage}, or creates one if none is
   * associated with the coverage.
   *
   * @param securityCoverage the {@link SecurityCoverage}
   * @return the updated or newly created {@link Scenario}
   */
  public Scenario updateOrCreateScenarioFromSecurityCoverage(SecurityCoverage securityCoverage) {
    if (securityCoverage.getScenario() != null) {
      return scenarioRepository
          .findById(securityCoverage.getScenario().getId())
          .map(existing -> updateScenarioFromSecurityCoverage(existing, securityCoverage))
          .orElseGet(() -> createAndInitializeScenario(securityCoverage));
    }
    return createAndInitializeScenario(securityCoverage);
  }

  private Scenario createAndInitializeScenario(SecurityCoverage securityCoverage) {
    Scenario scenario = new Scenario();
    updatePropertiesFromSecurityCoverage(scenario, securityCoverage);
    return scenarioService.createScenario(scenario);
  }

  private Scenario updateScenarioFromSecurityCoverage(
      Scenario scenario, SecurityCoverage securityCoverage) {
    updatePropertiesFromSecurityCoverage(scenario, securityCoverage);
    return scenarioService.updateScenario(scenario);
  }

  private void updatePropertiesFromSecurityCoverage(Scenario scenario, SecurityCoverage sa) {
    scenario.setSecurityCoverage(sa);
    scenario.setName(sa.getName());
    scenario.setDescription(sa.getDescription());
    scenario.setSeverity(Scenario.SEVERITY.high);
    scenario.setMainFocus(Scenario.MAIN_FOCUS_INCIDENT_RESPONSE);
    scenario.setCategory(ATTACK_SCENARIO);

    Instant start = sa.getPeriodStart();
    Instant end = sa.getPeriodEnd();

    scenario.setRecurrenceStart(start);
    scenario.setRecurrenceEnd(end);

    String cron = cronService.getCronExpression(sa.getScheduling(), start);
    scenario.setRecurrence(cron);

    scenario.setTags(tagService.fetchTagsFromLabels(sa.getLabels()));
  }

  public Bundle createBundleFromSendJobs(List<SecurityCoverageSendJob> securityCoverageSendJobs)
      throws ParsingException, JsonProcessingException {
    List<ObjectBase> objects = new ArrayList<>();
    for (SecurityCoverageSendJob securityCoverageSendJob : securityCoverageSendJobs) {
      SecurityCoverage sa = securityCoverageSendJob.getSimulation().getSecurityCoverage();
      if (sa == null) {
        continue;
      }

      Exercise ex = securityCoverageSendJob.getSimulation();
      objects.addAll(this.getCoverageForSimulation(ex));
    }

    return new Bundle(new Identifier("bundle", UUID.randomUUID().toString()), objects);
  }

  private List<ObjectBase> getCoverageForSimulation(Exercise exercise)
      throws ParsingException, JsonProcessingException {
    List<ObjectBase> objects = new ArrayList<>();

    // create the main coverage object
    SecurityCoverage assessment = exercise.getSecurityCoverage();
    DomainObject coverage = (DomainObject) stixParser.parseObject(assessment.getContent());
    coverage.setProperty(CommonProperties.MODIFIED.toString(), new Timestamp(Instant.now()));
    coverage.setProperty(ExtendedProperties.COVERAGE.toString(), getOverallCoverage(exercise));
    objects.add(coverage);

    // start and stop times
    Optional<Timestamp> sroStartTime = exercise.getStart().map(Timestamp::new);
    Optional<Timestamp> sroStopTime =
        exerciseService.getLatestValidityDate(exercise).map(Timestamp::new);

    for (StixRefToExternalRef stixRef : exercise.getSecurityCoverage().getAttackPatternRefs()) {
      BaseType<?> attackPatternCoverage =
          getAttackPatternCoverage(stixRef.getExternalRef(), exercise);
      boolean covered = !((Map<String, BaseType<?>>) attackPatternCoverage.getValue()).isEmpty();
      RelationshipObject sro =
          new RelationshipObject(
              new HashMap<>(
                  Map.of(
                      CommonProperties.ID.toString(),
                      new Identifier(ObjectTypes.RELATIONSHIP.toString(), exercise.getId()),
                      CommonProperties.TYPE.toString(),
                      new StixString(ObjectTypes.RELATIONSHIP.toString()),
                      RelationshipObject.Properties.RELATIONSHIP_TYPE.toString(),
                      new StixString("has-assessed"),
                      RelationshipObject.Properties.SOURCE_REF.toString(),
                      coverage.getId(),
                      RelationshipObject.Properties.TARGET_REF.toString(),
                      new Identifier(stixRef.getStixRef()),
                      ExtendedProperties.COVERED.toString(),
                      new io.openbas.stix.types.Boolean(covered))));
      sroStartTime.ifPresent(
          instant -> sro.setProperty(RelationshipObject.Properties.START_TIME.toString(), instant));
      sroStopTime.ifPresent(
          instant -> sro.setProperty(RelationshipObject.Properties.STOP_TIME.toString(), instant));
      if (covered) {
        sro.setProperty(ExtendedProperties.COVERAGE.toString(), attackPatternCoverage);
      }
      objects.add(sro);
    }

    for (SecurityPlatform securityPlatform : assetService.securityPlatforms()) {
      DomainObject platformIdentity = securityPlatform.toStixDomainObject();
      objects.add(platformIdentity);

      BaseType<?> platformCoverage = getOverallCoveragePerPlatform(exercise, securityPlatform);
      boolean covered = !((Map<String, BaseType<?>>) platformCoverage.getValue()).isEmpty();
      RelationshipObject sro =
          new RelationshipObject(
              new HashMap<>(
                  Map.of(
                      CommonProperties.ID.toString(),
                      new Identifier(
                          ObjectTypes.RELATIONSHIP.toString(), UUID.randomUUID().toString()),
                      CommonProperties.TYPE.toString(),
                      new StixString(ObjectTypes.RELATIONSHIP.toString()),
                      RelationshipObject.Properties.RELATIONSHIP_TYPE.toString(),
                      new StixString("has-assessed"),
                      RelationshipObject.Properties.SOURCE_REF.toString(),
                      coverage.getId(),
                      RelationshipObject.Properties.TARGET_REF.toString(),
                      platformIdentity.getId(),
                      ExtendedProperties.COVERED.toString(),
                      new io.openbas.stix.types.Boolean(covered))));
      sroStartTime.ifPresent(
          instant -> sro.setProperty(RelationshipObject.Properties.START_TIME.toString(), instant));
      sroStopTime.ifPresent(
          instant -> sro.setProperty(RelationshipObject.Properties.STOP_TIME.toString(), instant));
      if (covered) {
        sro.setProperty(ExtendedProperties.COVERAGE.toString(), platformCoverage);
      }
      objects.add(sro);
    }

    return objects;
  }

  private BaseType<?> getOverallCoverage(Exercise exercise) {
    return computeCoverageFromInjects(exercise.getInjects());
  }

  private BaseType<?> getOverallCoveragePerPlatform(
      Exercise exercise, SecurityPlatform securityPlatform) {
    return computeCoverageFromInjects(exercise.getInjects(), securityPlatform);
  }

  private BaseType<?> getAttackPatternCoverage(String externalRef, Exercise exercise) {
    List<AttackPattern> apList =
        attackPatternService.getAttackPatternsByExternalIds(Set.of(externalRef));
    Optional<AttackPattern> ap = apList.stream().findFirst();
    if (ap.isEmpty()) {
      return uncovered();
    }

    // get all injects involved in attack pattern
    List<Inject> injects =
        exercise.getInjects().stream()
            .filter(
                i ->
                    i.getInjectorContract().isPresent()
                        && i.getInjectorContract().get().getAttackPatterns().stream()
                            .anyMatch(
                                attackPattern -> attackPattern.getId().equals(ap.get().getId())))
            .toList();
    if (injects.isEmpty()) {
      return uncovered();
    }

    return computeCoverageFromInjects(injects);
  }

  private BaseType<?> computeCoverageFromInjects(
      List<Inject> injects, SecurityPlatform securityPlatform) {
    List<InjectExpectationResultUtils.ExpectationResultsByType> coverageResults =
        resultUtils.computeGlobalExpectationResultsForPlatform(
            injects.stream().map(Inject::getId).collect(Collectors.toSet()), securityPlatform);

    Map<String, BaseType<?>> coverageValues = new HashMap<>();
    for (InjectExpectationResultUtils.ExpectationResultsByType result : coverageResults) {
      coverageValues.put(
          result.type().name(), new StixString(String.valueOf(result.getSuccessRate())));
    }
    return new io.openbas.stix.types.Dictionary(coverageValues);
  }

  private BaseType<?> computeCoverageFromInjects(List<Inject> injects) {
    List<InjectExpectationResultUtils.ExpectationResultsByType> coverageResults =
        resultUtils.computeGlobalExpectationResults(
            injects.stream().map(Inject::getId).collect(Collectors.toSet()));

    Map<String, BaseType<?>> coverageValues = new HashMap<>();
    for (InjectExpectationResultUtils.ExpectationResultsByType result : coverageResults) {
      coverageValues.put(
          result.type().name(), new StixString(String.valueOf(result.getSuccessRate())));
    }
    return new io.openbas.stix.types.Dictionary(coverageValues);
  }

  private BaseType<?> uncovered() {
    return new io.openbas.stix.types.Dictionary(new HashMap<>());
  }
}
