package io.openaev.service.stix;

import static io.openaev.helper.CryptoHelper.md5Hex;
import static io.openaev.rest.payload.service.PayloadService.DYNAMIC_DNS_RESOLUTION_HOSTNAME_KEY;
import static io.openaev.stix.objects.constants.CommonProperties.MODIFIED;
import static io.openaev.utils.constants.StixConstants.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.aop.lock.Lock;
import io.openaev.aop.lock.LockResourceType;
import io.openaev.config.OpenAEVConfig;
import io.openaev.database.model.*;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.database.repository.SecurityCoverageRepository;
import io.openaev.opencti.connectors.impl.SecurityCoverageConnector;
import io.openaev.opencti.connectors.service.OpenCTIConnectorService;
import io.openaev.opencti.errors.ConnectorError;
import io.openaev.rest.attack_pattern.service.AttackPatternService;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.rest.tag.TagService;
import io.openaev.rest.vulnerability.service.VulnerabilityService;
import io.openaev.service.PreviewFeatureService;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.service.stix.error.BundleValidationError;
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
import io.openaev.stix.types.Boolean;
import io.openaev.stix.types.Dictionary;
import io.openaev.utils.InjectExpectationResultUtils;
import io.openaev.utils.ResultUtils;
import io.openaev.utils.SecurityCoverageUtils;
import io.openaev.utils.StringUtils;
import io.openaev.utils.time.TimeUtils;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class SecurityCoverageService {

  private final ScenarioService scenarioService;
  private final SecurityCoverageInjectService securityCoverageInjectService;
  private final TagService tagService;
  private final AttackPatternService attackPatternService;
  private final InjectService injectService;
  private final ResultUtils resultUtils;
  private final ExerciseService exerciseService;

  private final ScenarioRepository scenarioRepository;
  private final SecurityCoverageRepository securityCoverageRepository;

  private final Parser stixParser;
  @Resource private OpenAEVConfig openAEVConfig;
  private final ObjectMapper objectMapper;
  private final VulnerabilityService vulnerabilityService;
  private final OpenCTIConnectorService openCTIConnectorService;
  private final PreviewFeatureService previewFeatureService;

  // FIXME: don't access the connector directly when we deal with multiple origins
  private final SecurityCoverageConnector connector;

  private final SecurityCoverageUtils securityCoverageUtils;

  /**
   * Creates or updates a security coverage and the associated scenario, and updates the
   * corresponding connector.
   *
   * @param securityCoverageStixId the STIX ID of the security-coverage object in the bundle
   * @param securityCoverageObj the SDO representing the security coverage
   * @param bundle the full bundle, also containing the security coverage SDO
   * @throws ParsingException if the STIX bundle is malformed
   * @throws BundleValidationError if the STIX bundle is obsolete or already stored
   * @throws ConnectorError there was an issue communicating with the connector
   * @throws IOException there is an issue with serialisation
   */
  @Lock(type = LockResourceType.SECURITY_COVERAGE, key = "#securityCoverageStixId")
  @Transactional(rollbackFor = Exception.class)
  public Scenario handleSecurityCoverageProcessing(
      String securityCoverageStixId, ObjectBase securityCoverageObj, Bundle bundle)
      throws ParsingException, BundleValidationError, ConnectorError, IOException {
    String bundleHash = md5Hex(bundle.toStix(objectMapper).toString());

    SecurityCoverage securityCoverage =
        buildSecurityCoverageFromStix(
            securityCoverageObj, bundle, securityCoverageStixId, bundleHash);
    Scenario scenario = buildScenarioFromSecurityCoverage(securityCoverage);

    // FIXME: extract this behaviour into an async worker
    pushSecurityCoverageBundleWithExternalURI(scenario);
    return scenario;
  }

  /**
   * Maps a validated STIX object to a {@link SecurityCoverage}, sets optional fields, extracts
   * attack patterns, and persists it.
   *
   * @param stixCoverageObj parsed object from Stix bundle related to Security Coverage
   * @param bundle the STIX bundle
   * @param externalId Security coverage external ID
   * @param stixJsonHash MD5 hash of the STIX JSON content
   * @return the saved {@link SecurityCoverage} object
   * @throws ParsingException if the STIX bundle is malformed
   * @throws BundleValidationError if the STIX bundle is obsolete or already stored
   */
  private SecurityCoverage buildSecurityCoverageFromStix(
      ObjectBase stixCoverageObj, Bundle bundle, String externalId, String stixJsonHash)
      throws ParsingException, BundleValidationError {

    SecurityCoverage securityCoverage = getByExternalIdOrCreateSecurityCoverage(externalId);

    // Validations related to the pertinence of the received bundle
    checkExistingBundle(externalId, stixJsonHash, securityCoverage);
    checkLastBundle(stixCoverageObj, externalId, securityCoverage);

    securityCoverage.setExternalId(externalId);
    securityCoverage.setBundleHashMd5(stixJsonHash);

    String name = stixCoverageObj.getRequiredProperty(STIX_NAME);
    securityCoverage.setName(name);

    String coveredRef = stixCoverageObj.getRequiredProperty(STIX_COVERED_REF);
    securityCoverage.setExternalUrl(connector.getUrl() + "/dashboard/id/" + coveredRef);

    // Optional fields
    stixCoverageObj.setIfPresent(STIX_DESCRIPTION, securityCoverage::setDescription);

    // labels
    Set<String> labels = new HashSet<>();
    if (stixCoverageObj.hasProperty(CommonProperties.LABELS)
        && stixCoverageObj.getProperty(CommonProperties.LABELS).getValue() != null) {
      for (StixString stixString :
          (List<StixString>) stixCoverageObj.getProperty(CommonProperties.LABELS).getValue()) {
        labels.add(stixString.getValue());
      }
    }
    securityCoverage.setLabels(labels);

    // platform affinity
    Set<String> platformAffinity = new HashSet<>();
    if (stixCoverageObj.hasProperty(STIX_PLATFORMS_AFFINITY)
        && stixCoverageObj.getProperty(STIX_PLATFORMS_AFFINITY).getValue() != null) {
      for (StixString stixString :
          (List<StixString>) stixCoverageObj.getProperty(STIX_PLATFORMS_AFFINITY).getValue()) {
        platformAffinity.add(stixString.getValue());
      }
    }
    securityCoverage.setPlatformsAffinity(platformAffinity);

    // type affinity
    String typeAffinity = null;
    if (stixCoverageObj.hasProperty(STIX_TYPE_AFFINITY)
        && stixCoverageObj.getProperty(STIX_TYPE_AFFINITY).getValue() != null) {
      typeAffinity = ((StixString) stixCoverageObj.getProperty(STIX_TYPE_AFFINITY)).getValue();
    }
    securityCoverage.setTypeAffinity(typeAffinity);

    // Extract Attack Patterns
    securityCoverage.setAttackPatternRefs(
        securityCoverageUtils.extractObjectReferences(
            bundle.findByType(ObjectTypes.ATTACK_PATTERN)));

    // Extract vulnerabilities
    securityCoverage.setVulnerabilitiesRefs(
        securityCoverageUtils.extractObjectReferences(
            bundle.findByType(ObjectTypes.VULNERABILITY)));

    // Extract indicators
    securityCoverage.setIndicatorsRefs(
        securityCoverageUtils.extractObjectReferences(bundle.findByType(ObjectTypes.INDICATOR)));

    // Extract artifacts
    securityCoverage.setArtifactsRefs(
        securityCoverageUtils.extractObjectReferences(bundle.findByType(ObjectTypes.ARTIFACT)));

    // Default Fields
    String scheduling = stixCoverageObj.getOptionalProperty(STIX_PERIODICITY, "");
    securityCoverage.setScheduling(scheduling);

    // security coverage scenario overall duration
    securityCoverage.setDuration(stixCoverageObj.getOptionalProperty(STIX_DURATION, ""));

    // Period Start
    Dictionary extensionObj =
        (Dictionary) stixCoverageObj.getExtension(ExtendedProperties.OPENCTI_EXTENSION_DEFINITION);
    if (extensionObj.has(STIX_CREATED_AT)) {
      String createdAt = (String) extensionObj.get(STIX_CREATED_AT).getValue();
      securityCoverage.setPeriodStart(Instant.parse(createdAt));
    }

    securityCoverage.setContent(stixCoverageObj.toStix(objectMapper).toString());
    stixCoverageObj.setInstantIfPresent(MODIFIED, securityCoverage::setStixModified);

    log.info("Saving Security coverage with external ID: {}", securityCoverage.getExternalId());
    return save(securityCoverage);
  }

  /**
   * Ensures the incoming STIX object is newer than the stored one. Throws an error if the STIX
   * modified date is missing, invalid, or not newer.
   */
  private static void checkLastBundle(
      ObjectBase stixCoverageObj, String externalId, SecurityCoverage securityCoverage)
      throws ParsingException, BundleValidationError {
    // Check If stix coverage is the last one
    Object modifiedObj = stixCoverageObj.getProperty(MODIFIED).getValue();

    if (modifiedObj == null) {
      throw new ParsingException("STIX object missing mandatory modified date");
    }

    Instant stixModified;
    try {
      stixModified = Instant.parse(modifiedObj.toString());
    } catch (Exception e) {
      throw new ParsingException("Invalid STIX modified date format", e);
    }

    Instant currentModified = securityCoverage.getStixModified();

    // Last STIX modified date must be newer than the stored modified
    log.info(
        "SecurityCoverage Update Check: externalId={}, currentModified={}, stixModified={}",
        externalId,
        currentModified,
        stixModified);
    boolean isNewer = currentModified == null || stixModified.isAfter(currentModified);
    if (!isNewer) {
      throw new BundleValidationError(
          "The STIX package is obsolete because a newer version has already been computed.");
    }
  }

  /**
   * Checks whether the incoming STIX bundle is a duplicate by comparing its content hash to the
   * stored one.
   */
  private static void checkExistingBundle(
      String externalId, String stixJsonHash, SecurityCoverage securityCoverage)
      throws BundleValidationError {
    // Check if contentHash already matches (duplicate)
    if (stixJsonHash.equals(securityCoverage.getBundleHashMd5())) {
      log.info(
          "Duplicate STIX bundle detected for externalId={} -> returning existing object",
          externalId);
      // We could also simply return the existing security cover and avoid returning the error and
      // also avoid continue with the retry;
      throw new BundleValidationError(
          String.format(
              "Duplicate STIX bundle detected for externalId: %s -> returning existing object",
              externalId));
    }
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
    log.info(
        "Creating or Updating Scenario with ID: {} from Security coverage with external ID: {}",
        scenario.getId(),
        securityCoverage.getExternalId());

    return scenario;
  }

  /**
   * Enrich and push the security coverage to OpenCTI. This injects the OpenAEV scenario external
   * URL into the STIX object.
   *
   * @param scenario The scenario containing the security coverage.
   * @throws ParsingException If STIX parsing fails.
   * @throws ConnectorError If the OpenCTI push fails.
   */
  public void pushSecurityCoverageBundleWithExternalURI(Scenario scenario)
      throws ParsingException, ConnectorError, IOException {
    if (openCTIConnectorService.getConnectorBase().isEmpty()) {
      return;
    }

    SecurityCoverage coverage = scenario.getSecurityCoverage();
    String externalLink = openAEVConfig.getBaseUrl() + "/admin/scenarios/" + scenario.getId();

    DomainObject sdo = (DomainObject) stixParser.parseObject(coverage.getContent());
    sdo.setProperty(CommonProperties.EXTERNAL_URI.toString(), new StixString(externalLink));

    Bundle bundle =
        new Bundle(new Identifier("bundle", UUID.randomUUID().toString()), List.of(sdo));

    openCTIConnectorService.pushSecurityCoverageStixBundle(bundle);
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
    scenario.setTypeAffinity(sa.getTypeAffinity());
    scenario.setMainFocus(Scenario.MAIN_FOCUS_INCIDENT_RESPONSE);
    scenario.setExternalUrl(sa.getExternalUrl());
    scenario.setCategory(ATTACK_SCENARIO);
    setRecurrence(scenario, sa);
    scenario.setTags(
        tagService.findOrCreateTagsFromNames(
            sa.getPlatformsAffinity().stream()
                .map("security coverage: %s"::formatted)
                .collect(Collectors.toSet())));
  }

  /**
   * Set recurrence for the scenario coming from OpenCTI. The scenario will start immediately after
   * the save
   *
   * @param scenario
   * @param securityCoverage
   */
  private void setRecurrence(Scenario scenario, SecurityCoverage securityCoverage) {
    if (scenario.getRecurrence() == null) {
      // schedule first start in 2 minutes
      // so that it is picked up soon after setting it up
      Instant start =
          Instant.now()
              .plus(2, ChronoUnit.MINUTES)
              .atZone(ZoneId.of("UTC"))
              .truncatedTo(ChronoUnit.SECONDS)
              .toInstant();
      if (!StringUtils.isBlank(securityCoverage.getScheduling())) {
        scenario.setRecurrenceStart(start);
        scenario.setRecurrence(securityCoverage.getScheduling());
        if (!StringUtils.isBlank(securityCoverage.getDuration())) {
          scenario.setRecurrenceEnd(
              TimeUtils.incrementInstant(
                  start,
                  TimeUtils.ISO8601PeriodToTemporalIncrement(securityCoverage.getDuration())));
        }
      }
    }
  }

  public Bundle createBundleFromSendJobs(List<SecurityCoverageSendJob> securityCoverageSendJobs)
      throws ParsingException, JsonProcessingException {
    List<ObjectBase> objects = new ArrayList<>();
    for (SecurityCoverageSendJob securityCoverageSendJob : securityCoverageSendJobs) {
      SecurityCoverage sa = securityCoverageSendJob.getSimulation().getSecurityCoverage();
      if (sa == null) {
        continue;
      }

      Exercise simulation = securityCoverageSendJob.getSimulation();
      objects.addAll(this.getCoverageForSimulation(simulation));
    }

    return new Bundle(new Identifier("bundle", UUID.randomUUID().toString()), objects);
  }

  private List<ObjectBase> getCoverageForSimulation(Exercise simulation)
      throws ParsingException, JsonProcessingException {
    List<ObjectBase> objects = new ArrayList<>();

    // create the main coverage object
    SecurityCoverage assessment = simulation.getSecurityCoverage();
    DomainObject coverage = (DomainObject) stixParser.parseObject(assessment.getContent());
    coverage.setProperty(CommonProperties.MODIFIED.toString(), new Timestamp(Instant.now()));
    coverage.setProperty(CommonProperties.AUTO_ENRICHMENT_DISABLE.toString(), new Boolean(false));

    String externalLink;
    if (simulation.getScenario() != null) {
      externalLink =
          openAEVConfig.getBaseUrl() + "/admin/scenarios/" + simulation.getScenario().getId();
    } else {
      externalLink = openAEVConfig.getBaseUrl() + "/admin/simulations/" + simulation.getId();
    }

    coverage.setProperty(CommonProperties.EXTERNAL_URI.toString(), new StixString(externalLink));
    coverage.setProperty(ExtendedProperties.COVERAGE.toString(), getOverallCoverage(simulation));
    objects.add(coverage);

    // start and stop times
    Optional<Timestamp> sroStartTime = simulation.getStart().map(Timestamp::new);
    Optional<Timestamp> sroStopTime =
        exerciseService.getLatestValidityDate(simulation).map(Timestamp::new);

    sroStartTime.ifPresent(
        instant -> coverage.setProperty(ExtendedProperties.VALID_FROM.toString(), instant));
    sroStartTime.ifPresent(
        instant -> coverage.setProperty(ExtendedProperties.LAST_RESULT.toString(), instant));
    sroStopTime.ifPresent(
        instant -> coverage.setProperty(ExtendedProperties.VALID_TO.toString(), instant));

    // Process coverage refs by stix object: attack patterns
    processCoverageRefs(
        simulation.getSecurityCoverage().getAttackPatternRefs(),
        simulation,
        this::getAttackPatternCoverage,
        coverage.getId(),
        sroStartTime,
        sroStopTime,
        objects);

    if (previewFeatureService.isFeatureEnabled(
        PreviewFeature.STIX_SECURITY_COVERAGE_FOR_VULNERABILITIES)) {
      // Process coverage refs by stix object: vulnerabilities
      processCoverageRefs(
          simulation.getSecurityCoverage().getVulnerabilitiesRefs(),
          simulation,
          this::getVulnerabilityCoverage,
          coverage.getId(),
          sroStartTime,
          sroStopTime,
          objects);
    }

    if (simulation.getSecurityCoverage().getIndicatorsRefs() != null
        && !simulation.getSecurityCoverage().getIndicatorsRefs().isEmpty()) {
      processCoverageRefs(
          simulation.getSecurityCoverage().getIndicatorsRefs(),
          simulation,
          this::getDnsIndicatorCoverage,
          coverage.getId(),
          sroStartTime,
          sroStopTime,
          objects);
    }

    if (simulation.getSecurityCoverage().getArtifactsRefs() != null
        && !simulation.getSecurityCoverage().getArtifactsRefs().isEmpty()) {
      processCoverageRefs(
          simulation.getSecurityCoverage().getArtifactsRefs(),
          simulation,
          this::getArtifactCoverage,
          coverage.getId(),
          sroStartTime,
          sroStopTime,
          objects);
    }

    for (SecurityPlatform securityPlatform :
        injectService.extractSecurityPlatforms(simulation.getInjects())) {
      DomainObject platformIdentity = securityPlatform.toStixDomainObject();
      objects.add(platformIdentity);

      BaseType<?> platformCoverage = getOverallCoveragePerPlatform(simulation, securityPlatform);
      boolean covered = !((List<?>) platformCoverage.getValue()).isEmpty();
      RelationshipObject sro =
          new RelationshipObject(
              new HashMap<>(
                  Map.of(
                      CommonProperties.ID.toString(),
                      generateRelationship(
                          coverage.getId().getValue(), platformIdentity.getId().getValue()),
                      CommonProperties.TYPE.toString(),
                      new StixString(ObjectTypes.RELATIONSHIP.toString()),
                      RelationshipObject.Properties.RELATIONSHIP_TYPE.toString(),
                      new StixString("has-covered"),
                      RelationshipObject.Properties.SOURCE_REF.toString(),
                      coverage.getId(),
                      RelationshipObject.Properties.TARGET_REF.toString(),
                      platformIdentity.getId(),
                      ExtendedProperties.COVERED.toString(),
                      new io.openaev.stix.types.Boolean(covered))));
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

  private void processCoverageRefs(
      Set<StixRefToExternalRef> refs,
      Exercise simulation,
      BiFunction<List<String>, Exercise, BaseType<?>> coverageFunction,
      Identifier coverageId,
      Optional<Timestamp> sroStartTime,
      Optional<Timestamp> sroStopTime,
      List<ObjectBase> objects) {
    for (StixRefToExternalRef stixRef : refs) {
      BaseType<?> coverageResult = coverageFunction.apply(stixRef.getExternalRefs(), simulation);
      boolean covered = !((List<?>) coverageResult.getValue()).isEmpty();

      RelationshipObject sro =
          new RelationshipObject(
              new HashMap<>(
                  Map.of(
                      CommonProperties.ID.toString(),
                      generateRelationship(coverageId.getValue(), stixRef.getStixRef()),
                      CommonProperties.TYPE.toString(),
                      new StixString(ObjectTypes.RELATIONSHIP.toString()),
                      RelationshipObject.Properties.RELATIONSHIP_TYPE.toString(),
                      new StixString("has-covered"),
                      RelationshipObject.Properties.SOURCE_REF.toString(),
                      coverageId,
                      RelationshipObject.Properties.TARGET_REF.toString(),
                      new Identifier(stixRef.getStixRef()),
                      ExtendedProperties.COVERED.toString(),
                      new io.openaev.stix.types.Boolean(covered))));

      sroStartTime.ifPresent(
          instant -> sro.setProperty(RelationshipObject.Properties.START_TIME.toString(), instant));
      sroStopTime.ifPresent(
          instant -> sro.setProperty(RelationshipObject.Properties.STOP_TIME.toString(), instant));

      if (covered) {
        sro.setProperty(ExtendedProperties.COVERAGE.toString(), coverageResult);
      }
      objects.add(sro);
    }
  }

  private BaseType<?> getOverallCoverage(Exercise simulation) {
    return computeCoverageFromInjects(simulation.getInjects());
  }

  private BaseType<?> getOverallCoveragePerPlatform(
      Exercise simulation, SecurityPlatform securityPlatform) {
    return computeCoverageFromInjects(simulation.getInjects(), securityPlatform);
  }

  private BaseType<?> getVulnerabilityCoverage(List<String> externalRefs, Exercise simulation) {
    return getCoverage(
        externalRefs,
        simulation,
        ids -> vulnerabilityService.getVulnerabilitiesByExternalIds(new HashSet<>(ids)),
        inject -> {
          if (inject.getInjectorContract().isPresent()) {
            return inject.getInjectorContract().get().getVulnerabilities();
          }
          return Collections.emptyList();
        },
        Vulnerability::getId);
  }

  private BaseType<?> getAttackPatternCoverage(List<String> externalRefs, Exercise simulation) {
    return getCoverage(
        externalRefs,
        simulation,
        ids -> attackPatternService.getAttackPatternsByExternalIds(new HashSet<>(ids)),
        inject -> {
          if (inject.getInjectorContract().isPresent()) {
            return inject.getInjectorContract().get().getAttackPatterns();
          }
          return Collections.emptyList();
        },
        AttackPattern::getId);
  }

  private BaseType<?> getDnsIndicatorCoverage(List<String> externalRefs, Exercise simulation) {
    return getCoverage(
        externalRefs,
        simulation,
        hostnames ->
            simulation.getInjects().stream()
                .filter(
                    inject ->
                        inject.getContent().has(DYNAMIC_DNS_RESOLUTION_HOSTNAME_KEY)
                            && hostnames.contains(
                                (inject
                                    .getContent()
                                    .get(DYNAMIC_DNS_RESOLUTION_HOSTNAME_KEY)
                                    .textValue())))
                .toList(),
        inject ->
            Optional.ofNullable(inject)
                .map(Collections::singletonList)
                .orElse(Collections.emptyList()),
        Inject::getId);
  }

  private BaseType<?> getArtifactCoverage(List<String> externalRefs, Exercise simulation) {
    return getCoverage(
        externalRefs,
        simulation,
        documentIds ->
            simulation.getInjects().stream()
                .filter(
                    inject ->
                        inject.getPayload().isPresent()
                            && inject.getPayload().get() instanceof FileDrop fileDrop
                            && documentIds.contains(fileDrop.getFileDropFile().getId()))
                .toList(),
        inject ->
            Optional.ofNullable(inject)
                .map(Collections::singletonList)
                .orElse(Collections.emptyList()),
        Inject::getId);
  }

  private <T> BaseType<?> getCoverage(
      List<String> externalRefs,
      Exercise simulation,
      Function<List<String>, Collection<T>> entityFetcher,
      Function<Inject, Collection<T>> contractExtractor,
      Function<T, String> idExtractor) {
    // fetch entity
    Optional<T> entity = entityFetcher.apply(externalRefs).stream().findFirst();
    if (entity.isEmpty()) {
      return uncovered();
    }

    // find matching injects
    List<Inject> injects =
        simulation.getInjects().stream()
            .filter(
                i ->
                    contractExtractor.apply(i).stream()
                        .anyMatch(
                            e -> idExtractor.apply(e).equals(idExtractor.apply(entity.get()))))
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

    return computeCoverage(coverageResults);
  }

  private BaseType<?> computeCoverageFromInjects(List<Inject> injects) {
    List<InjectExpectationResultUtils.ExpectationResultsByType> coverageResults =
        resultUtils.computeGlobalExpectationResults(
            injects.stream().map(Inject::getId).collect(Collectors.toSet()));

    return computeCoverage(coverageResults);
  }

  @NotNull
  private BaseType<?> computeCoverage(
      List<InjectExpectationResultUtils.ExpectationResultsByType> coverageResults) {
    List<Complex<?>> coverageValues = new ArrayList<>();
    for (InjectExpectationResultUtils.ExpectationResultsByType result : coverageResults) {
      CoverageResult cov =
          new CoverageResult(
              result.type().name(), result.getSuccessRate() * 100); // force percentage points
      coverageValues.add(new Complex<>(cov));
    }
    return new io.openaev.stix.types.List<>(coverageValues);
  }

  private BaseType<?> uncovered() {
    return new io.openaev.stix.types.List<>(new ArrayList<>());
  }

  private Identifier generateRelationship(String sourceId, String targetId) {
    return new Identifier(
        ObjectTypes.RELATIONSHIP.toString(),
        UUID.nameUUIDFromBytes((sourceId + targetId).getBytes(StandardCharsets.UTF_8)).toString());
  }
}
