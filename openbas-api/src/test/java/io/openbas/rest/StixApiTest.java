package io.openbas.rest;

import static io.openbas.rest.StixApi.STIX_URI;
import static io.openbas.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openbas.service.TagRuleService.OPENCTI_TAG_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.openbas.IntegrationTest;
import io.openbas.database.model.Inject;
import io.openbas.database.model.Scenario;
import io.openbas.database.model.StixRefToExternalRef;
import io.openbas.database.repository.InjectRepository;
import io.openbas.database.repository.ScenarioRepository;
import io.openbas.database.repository.SecurityCoverageRepository;
import io.openbas.utils.fixtures.composers.AttackPatternComposer;
import io.openbas.utils.fixtures.files.AttackPatternFixture;
import io.openbas.utils.mockUser.WithMockAdminUser;
import jakarta.annotation.Resource;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@TestInstance(PER_CLASS)
@Transactional
@WithMockAdminUser
@DisplayName("STIX API Integration Tests")
class StixApiTest extends IntegrationTest {

  public static final String T_1531 = "T1531";
  public static final String T_1003 = "T1003";

  @Resource protected ObjectMapper mapper;
  @Autowired private MockMvc mvc;
  @Autowired private EntityManager entityManager;

  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private SecurityCoverageRepository securityCoverageRepository;

  @Autowired private AttackPatternComposer attackPatternComposer;

  private String stixSecurityCoverage;
  private String stixSecurityCoverageWithoutTtps;

  @BeforeEach
  void setUp() throws Exception {
    attackPatternComposer.reset();
    try (FileInputStream fis1 =
            new FileInputStream("src/test/resources/stix-bundles/security-coverage.json");
        FileInputStream fis2 =
            new FileInputStream(
                "src/test/resources/stix-bundles/security-coverage-without-ttps.json")) {

      stixSecurityCoverage = IOUtils.toString(fis1, StandardCharsets.UTF_8);
      stixSecurityCoverageWithoutTtps = IOUtils.toString(fis2, StandardCharsets.UTF_8);
    }

    attackPatternComposer
        .forAttackPattern(AttackPatternFixture.createAttackPatternsWithExternalId(T_1531))
        .persist();
    attackPatternComposer
        .forAttackPattern(AttackPatternFixture.createAttackPatternsWithExternalId(T_1003))
        .persist();
  }

  @AfterEach
  void afterEach() {
    attackPatternComposer.reset();
  }

  @Nested
  @DisplayName("Import STIX Bundles")
  class ImportStixBundles {

    @Test
    @DisplayName("Should return 400 when STIX bundle has no security coverage")
    void shouldReturnBadRequestWhenNoSecurityCoverage() throws Exception {
      String bundleWithoutCoverage =
          stixSecurityCoverage.replace("x-security-coverage", "x-other-type");

      mvc.perform(
              post(STIX_URI + "/process-bundle")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(bundleWithoutCoverage))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 400 when STIX bundle has multiple security coverages")
    void shouldReturnBadRequestWhenMultipleSecurityCoverages() throws Exception {
      // Simulate bundle with two identical security coverages
      String duplicatedCoverage =
          stixSecurityCoverage.replace("]", ", " + stixSecurityCoverage.split("\\[")[1]);

      mvc.perform(
              post(STIX_URI + "/process-bundle")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(duplicatedCoverage))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 400 when STIX JSON is malformed")
    void shouldReturnBadRequestWhenStixJsonIsInvalid() throws Exception {
      String invalidJson = "{ not-a-valid-json }";

      mvc.perform(
              post(STIX_URI + "/process-bundle")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(invalidJson))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 400 when STIX bundle has invalid structure")
    void shouldReturnBadRequestWhenStixStructureInvalid() throws Exception {
      String structurallyInvalidStix =
          """
              {
                "type": "bundle",
                "id": "bundle--1234"
                // Missing "objects" field
              }
              """;

      mvc.perform(
              post(STIX_URI + "/process-bundle")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(structurallyInvalidStix))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should create the scenario from stix bundle")
    void shouldCreateScenario() throws Exception {
      String response =
          mvc.perform(
                  post(STIX_URI + "/process-bundle")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(stixSecurityCoverage))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThat(response).isNotBlank();
      String scenarioId = JsonPath.read(response, "$.scenarioId");
      Scenario createdScenario = scenarioRepository.findById(scenarioId).orElseThrow();

      // -- ASSERT Scenario --
      assertThat(createdScenario.getName())
          .isEqualTo("Security Coverage Q3 2025 - Threat Report XYZ");
      assertThat(createdScenario.getDescription())
          .isEqualTo("Security coverage test plan for threat context XYZ.");
      assertThat(createdScenario.getSecurityCoverage().getExternalId())
          .isEqualTo("x-security-coverage--4c3b91e2-3b47-4f84-b2e6-d27e3f0581c1");
      assertThat(createdScenario.getRecurrence()).isEqualTo("0 0 14 * * *");
      assertThat(createdScenario.getTags().stream().map(tag -> tag.getName()).toList())
          .contains(OPENCTI_TAG_NAME);

      // -- ASSERT Security Coverage --
      assertThat(createdScenario.getSecurityCoverage().getThreatContextRef())
          .isEqualTo("report--453a2ac1-e111-57bf-8277-dbec448cd851");
      assertThat(createdScenario.getSecurityCoverage().getAttackPatternRefs()).hasSize(2);

      StixRefToExternalRef stixRef1 =
          new StixRefToExternalRef("attack-pattern--a24d97e6-401c-51fc-be24-8f797a35d1f1", T_1531);
      StixRefToExternalRef stixRef2 =
          new StixRefToExternalRef("attack-pattern--033921be-85df-5f05-8bc0-d3d9fc945db9", T_1003);

      assertThat(createdScenario.getSecurityCoverage().getAttackPatternRefs()).hasSize(2);
      assertTrue(
          createdScenario
              .getSecurityCoverage()
              .getAttackPatternRefs()
              .containsAll(List.of(stixRef1, stixRef2)));
      assertThat(createdScenario.getSecurityCoverage().getVulnerabilitiesRefs()).isNull();
      assertThat(createdScenario.getSecurityCoverage().getContent()).isNotBlank();

      // -- ASSERT Injects --
      Set<Inject> injects = injectRepository.findByScenarioId(scenarioId);
      assertThat(injects).hasSize(2);
    }

    @Test
    @DisplayName(
        "Should update scenario from same security coverage and keep same number inject when updated stix has the same attacks")
    void shouldUpdateScenarioAndKeepSameNumberInjectsWhenUpdatedStixHasSameAttacks()
        throws Exception {
      String createdResponse =
          mvc.perform(
                  post(STIX_URI + "/process-bundle")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(stixSecurityCoverage))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String scenarioId = JsonPath.read(createdResponse, "$.scenarioId");
      Scenario createdScenario = scenarioRepository.findById(scenarioId).orElseThrow();
      assertThat(createdScenario.getName())
          .isEqualTo("Security Coverage Q3 2025 - Threat Report XYZ");

      Set<Inject> injects = injectRepository.findByScenarioId(createdScenario.getId());
      assertThat(injects).hasSize(2);

      entityManager.flush();
      entityManager.clear();

      // Push same stix in order to check the number of created injects
      String updatedResponse =
          mvc.perform(
                  post(STIX_URI + "/process-bundle")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(stixSecurityCoverage))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      scenarioId = JsonPath.read(updatedResponse, "$.scenarioId");
      Scenario updatedScenario = scenarioRepository.findById(scenarioId).orElseThrow();
      assertThat(updatedScenario.getName())
          .isEqualTo("Security Coverage Q3 2025 - Threat Report XYZ");
      // ASSERT injects for updated stix
      injects = injectRepository.findByScenarioId(updatedScenario.getId());
      assertThat(injects).hasSize(2);
    }

    @Test
    @DisplayName(
        "Should update scenario from same security coverage but deleting injects when attack-objects are not defined in stix")
    void shouldUpdateScenarioAndDeleteInjectWhenStixNotContainsAttacks() throws Exception {
      String createdResponse =
          mvc.perform(
                  post(STIX_URI + "/process-bundle")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(stixSecurityCoverage))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String scenarioId = JsonPath.read(createdResponse, "$.scenarioId");
      Scenario createdScenario = scenarioRepository.findById(scenarioId).orElseThrow();
      assertThat(createdScenario.getName())
          .isEqualTo("Security Coverage Q3 2025 - Threat Report XYZ");

      Set<Inject> injects = injectRepository.findByScenarioId(createdScenario.getId());
      assertThat(injects).hasSize(2);

      entityManager.flush();
      entityManager.clear();

      // Push stix without object type attack-pattern
      String updatedResponse =
          mvc.perform(
                  post(STIX_URI + "/process-bundle")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(stixSecurityCoverageWithoutTtps))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      scenarioId = JsonPath.read(updatedResponse, "$.scenarioId");
      Scenario updatedScenario = scenarioRepository.findById(scenarioId).orElseThrow();
      assertThat(updatedScenario.getName())
          .isEqualTo("Security Coverage Q3 2025 - Threat Report XYZ -- UPDATED");

      // ASSERT injects for updated stix
      injects = injectRepository.findByScenarioId(updatedScenario.getId());
      assertThat(injects).hasSize(0);
    }

    @Test
    @DisplayName("Should not remove security coverage even if scenario is deleted")
    void shouldExistSecurityCoverage() throws Exception {

      String response =
          mvc.perform(
                  post(STIX_URI + "/process-bundle")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(stixSecurityCoverage))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      String scenarioId = JsonPath.read(response, "$.scenarioId");
      Scenario scenario = scenarioRepository.findById(scenarioId).orElseThrow();
      String securityCoverageId = scenario.getSecurityCoverage().getId();
      scenarioRepository.deleteById(response);

      assertThat(securityCoverageRepository.findByExternalId(securityCoverageId)).isNotNull();
    }

    @Test
    @DisplayName("Should not duplicate security coverage reference when scenario is duplicated")
    void shouldNotDuplicatedReferenceSecurityCoverage() throws Exception {

      String response =
          mvc.perform(
                  post(STIX_URI + "/process-bundle")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(stixSecurityCoverage))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String scenarioId = JsonPath.read(response, "$.scenarioId");

      String duplicated =
          mvc.perform(post(SCENARIO_URI + "/" + scenarioId).contentType(MediaType.APPLICATION_JSON))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      scenarioId = JsonPath.read(duplicated, "$.scenario_id");

      Scenario duplicatedScenario = scenarioRepository.findById(scenarioId).orElseThrow();

      assertThat(duplicatedScenario.getSecurityCoverage()).isNull();
    }
  }
}
