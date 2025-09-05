package io.openbas.service;

import static io.openbas.utils.SecurityCoverageUtils.extractAndValidateCoverage;
import static io.openbas.utils.SecurityCoverageUtils.extractObjectReferences;
import static io.openbas.utils.TimeUtils.getCronExpression;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openbas.database.model.Inject;
import io.openbas.database.model.Scenario;
import io.openbas.database.model.SecurityCoverage;
import io.openbas.database.repository.ScenarioRepository;
import io.openbas.database.repository.SecurityCoverageRepository;
import io.openbas.rest.tag.TagService;
import io.openbas.stix.objects.Bundle;
import io.openbas.stix.objects.ObjectBase;
import io.openbas.stix.parsing.Parser;
import io.openbas.stix.parsing.ParsingException;
import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class SecurityCoverageService {

  public static final String STIX_ID = "id";
  public static final String STIX_THREAT_CONTEXT_REF = "threat_context_ref";
  public static final String STIX_NAME = "name";
  public static final String STIX_DESCRIPTION = "description";
  public static final String STIX_LABELS = "labels";
  public static final String STIX_SCHEDULING = "scheduling";
  public static final String STIX_PERIOD_START = "period_start";
  public static final String STIX_PERIOD_END = "period_end";
  public static final String STIX_TYPE = "type";
  public static final String STIX_ATTACK_PATTERN_TYPE = "attack-pattern";
  public static final String STIX_VULNERABILITY_TYPE = "vulnerability";
  public static final String ONE_SHOT = "X";
  public static final String INCIDENT_RESPONSE = "incident-response";
  public static final String ATTACK_SCENARIO = "attack-scenario";

  private final ScenarioService scenarioService;
  private final TagService tagService;
  private final SecurityCoverageInjectService securityCoverageInjectService;

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
    String externalId = stixCoverageObj.getRequiredProperty(STIX_ID);
    SecurityCoverage securityCoverage = getByExternalIdOrCreateSecurityCoverage(externalId);
    securityCoverage.setExternalId(externalId);

    String threatContextRef = stixCoverageObj.getRequiredProperty(STIX_THREAT_CONTEXT_REF);
    securityCoverage.setThreatContextRef(threatContextRef);

    String name = stixCoverageObj.getRequiredProperty(STIX_NAME);
    securityCoverage.setName(name);

    // Optional fields
    stixCoverageObj.setIfPresent(STIX_DESCRIPTION, securityCoverage::setDescription);
    stixCoverageObj.setIfListPresent(STIX_LABELS, securityCoverage::setLabels);

    // Extract Attack Patterns
    securityCoverage.setAttackPatternRefs(
        extractObjectReferences(bundle.findByType(STIX_ATTACK_PATTERN_TYPE)));

    // Extract vulnerabilities
    securityCoverage.setVulnerabilitiesRefs(
        extractObjectReferences(bundle.findByType(STIX_VULNERABILITY_TYPE)));

    // Default Fields
    String scheduling = stixCoverageObj.getOptionalProperty(STIX_SCHEDULING, ONE_SHOT);
    securityCoverage.setScheduling(scheduling);

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
        securityCoverageInjectService.createdInjectsForScenario(scenario, securityCoverage);
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
    scenario.setMainFocus(INCIDENT_RESPONSE);
    scenario.setCategory(ATTACK_SCENARIO);

    Instant start = sa.getPeriodStart();
    Instant end = sa.getPeriodEnd();

    scenario.setRecurrenceStart(start);
    scenario.setRecurrenceEnd(end);

    String cron = getCronExpression(sa.getScheduling(), start);
    scenario.setRecurrence(cron);

    scenario.setTags(tagService.fetchTagsFromLabels(sa.getLabels()));
  }
}
