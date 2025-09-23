package io.openbas.api.stix_process;

import static io.openbas.api.stix_process.StixApi.STIX_URI;
import static io.openbas.injector_contract.InjectorContractContentUtilsTest.createContentWithFieldAsset;
import static io.openbas.injector_contract.InjectorContractContentUtilsTest.createContentWithFieldAssetGroup;
import static io.openbas.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openbas.service.TagRuleService.OPENCTI_TAG_NAME;
import static io.openbas.utils.fixtures.CveFixture.CVE_2023_48788;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.openbas.IntegrationTest;
import io.openbas.database.model.*;
import io.openbas.database.repository.InjectRepository;
import io.openbas.database.repository.ScenarioRepository;
import io.openbas.database.repository.SecurityCoverageRepository;
import io.openbas.utils.fixtures.*;
import io.openbas.utils.fixtures.composers.*;
import io.openbas.utils.fixtures.files.AttackPatternFixture;
import io.openbas.utils.mockUser.WithMockUser;
import jakarta.annotation.Resource;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@TestInstance(PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
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
  @Autowired private CveComposer vulnerabilityComposer;
  @Autowired private TagRuleComposer tagRuleComposer;
  @Autowired private AssetGroupComposer assetGroupComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private TagComposer tagComposer;

  @Autowired private InjectorFixture injectorFixture;

  private String stixSecurityCoverage;
  private String stixSecurityCoverageWithoutTtps;
  private String stixSecurityCoverageWithoutVulns;
  private String stixSecurityCoverageWithoutObjects;
  private String stixSecurityCoverageOnlyVulns;
  private AssetGroupComposer.Composer completeAssetGroup;
  private AssetGroupComposer.Composer emptyAssetGroup;

  @BeforeEach
  void setUp() throws Exception {
    attackPatternComposer.reset();
    try (FileInputStream complete =
            new FileInputStream("src/test/resources/stix-bundles/security-coverage.json");
        FileInputStream withoutAttacks =
            new FileInputStream(
                "src/test/resources/stix-bundles/security-coverage-without-ttps.json");
        FileInputStream withoutVulns =
            new FileInputStream(
                "src/test/resources/stix-bundles/security-coverage-without-vulns.json");
        FileInputStream withoutObjects =
            new FileInputStream(
                "src/test/resources/stix-bundles/security-coverage-without-objects.json");
        FileInputStream onlyVulns =
            new FileInputStream(
                "src/test/resources/stix-bundles/security-coverage-only-vulns.json")) {

      stixSecurityCoverage = IOUtils.toString(complete, StandardCharsets.UTF_8);
      stixSecurityCoverageWithoutTtps = IOUtils.toString(withoutAttacks, StandardCharsets.UTF_8);
      stixSecurityCoverageWithoutVulns = IOUtils.toString(withoutVulns, StandardCharsets.UTF_8);
      stixSecurityCoverageWithoutObjects = IOUtils.toString(withoutObjects, StandardCharsets.UTF_8);
      stixSecurityCoverageOnlyVulns = IOUtils.toString(onlyVulns, StandardCharsets.UTF_8);
    }

    attackPatternComposer
        .forAttackPattern(AttackPatternFixture.createAttackPatternsWithExternalId(T_1531))
        .persist();
    attackPatternComposer
        .forAttackPattern(AttackPatternFixture.createAttackPatternsWithExternalId(T_1003))
        .persist();

    Asset hostname =
        endpointComposer
            .forEndpoint(EndpointFixture.createEndpointOnlyWithHostname())
            .persist()
            .get();
    Asset seenIp =
        endpointComposer
            .forEndpoint(EndpointFixture.createEndpointOnlyWithSeenIP())
            .persist()
            .get();
    Asset localIp =
        endpointComposer
            .forEndpoint(EndpointFixture.createEndpointOnlyWithLocalIP())
            .persist()
            .get();

    emptyAssetGroup =
        assetGroupComposer
            .forAssetGroup(
                AssetGroupFixture.createAssetGroupWithAssets("no assets", new ArrayList<>()))
            .persist();

    completeAssetGroup =
        assetGroupComposer
            .forAssetGroup(
                AssetGroupFixture.createAssetGroupWithAssets(
                    "Complete", new ArrayList<>(Arrays.asList(hostname, seenIp, localIp))))
            .persist();

    injectorContractComposer
        .forInjectorContract(
            InjectorContractFixture.createInjectorContract(createContentWithFieldAsset()))
        .withInjector(injectorFixture.getWellKnownObasImplantInjector())
        .withVulnerability(
            vulnerabilityComposer.forCve(CveFixture.createDefaultCve("CVE-2025-56785")))
        .persist();

    injectorContractComposer
        .forInjectorContract(
            InjectorContractFixture.createInjectorContract(createContentWithFieldAssetGroup()))
        .withInjector(injectorFixture.getWellKnownObasImplantInjector())
        .withVulnerability(
            vulnerabilityComposer.forCve(CveFixture.createDefaultCve("CVE-2025-56786")))
        .persist();

    tagRuleComposer
        .forTagRule(new TagRule())
        .withTag(tagComposer.forTag(TagFixture.getTagWithText("empty-asset-group")))
        .withAssetGroup(emptyAssetGroup)
        .persist();

    tagRuleComposer
        .forTagRule(new TagRule())
        .withTag(tagComposer.forTag(TagFixture.getTagWithText("coverage")))
        .withAssetGroup(completeAssetGroup)
        .persist();

    tagRuleComposer
        .forTagRule(new TagRule())
        .withTag(tagComposer.forTag(TagFixture.getTagWithText("no-asset-groups")))
        .persist();
  }

  @AfterEach
  void afterEach() {
    attackPatternComposer.reset();
    vulnerabilityComposer.reset();
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
      assertThat(createdScenario.getSecurityCoverage().getAttackPatternRefs()).hasSize(2);

      StixRefToExternalRef stixRef1 =
          new StixRefToExternalRef("attack-pattern--a24d97e6-401c-51fc-be24-8f797a35d1f1", T_1531);
      StixRefToExternalRef stixRef2 =
          new StixRefToExternalRef("attack-pattern--033921be-85df-5f05-8bc0-d3d9fc945db9", T_1003);

      // -- Vulnerabilities --
      assertThat(createdScenario.getSecurityCoverage().getVulnerabilitiesRefs()).hasSize(1);

      StixRefToExternalRef stixRefVuln =
          new StixRefToExternalRef(
              "vulnerability--de1172d3-a3e8-51a8-9014-30e572f3b975", CVE_2023_48788);

      assertTrue(
          createdScenario
              .getSecurityCoverage()
              .getAttackPatternRefs()
              .containsAll(List.of(stixRef1, stixRef2)));
      assertThat(createdScenario.getSecurityCoverage().getVulnerabilitiesRefs())
          .containsAll(List.of(stixRefVuln));
      assertThat(createdScenario.getSecurityCoverage().getContent()).isNotBlank();

      // -- ASSERT Injects --
      Set<Inject> injects = injectRepository.findByScenarioId(scenarioId);
      assertThat(injects).hasSize(3);
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
      assertThat(injects).hasSize(3);

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
      assertThat(injects).hasSize(3);
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
      assertThat(injects).hasSize(3);

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
      assertThat(injects).hasSize(1); // After update with only one object type vulnerability
      Inject inject = injects.stream().findFirst().get();
      assertTrue(inject.getTitle().contains("[CVE-2023-48788]"));
      assertTrue(
          inject
              .getDescription()
              .contains(
                  "This placeholder is disabled because the Vulnerability CVE-2023-48788 is currently not covered. "
                      + "Please add the contracts related to this vulnerability."));
    }

    @Test
    @DisplayName(
        "Should update scenario from same security coverage but deleting injects when vulnerabilities are not defined in stix")
    void shouldUpdateScenarioAndDeleteInjectWhenStixNotContainsVulnerabilities() throws Exception {
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
      assertThat(injects).hasSize(3);

      entityManager.flush();
      entityManager.clear();

      // Push stix without object type attack-pattern
      String updatedResponse =
          mvc.perform(
                  post(STIX_URI + "/process-bundle")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(stixSecurityCoverageWithoutVulns))
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
      assertThat(injects).hasSize(1); // After update with only one object type vulnerability
      Inject inject = injects.stream().findFirst().get();
      assertTrue(inject.getTitle().contains("[T1003]"));
      assertTrue(
          inject
              .getDescription()
              .contains(
                  "This placeholder is disabled because the Attack Pattern T1003 is currently not covered. "
                      + "Please create the payloads for platform [any platform] and architecture [any architecture]."));
    }

    @Test
    @DisplayName(
        "Should update scenario from same security coverage but deleting injects when none objects are not defined in stix")
    void shouldUpdateScenarioAndDeleteInjectWhenStixNotContainsOtherObjects() throws Exception {
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
      assertThat(injects).hasSize(3);

      // Push stix without object type attack-pattern
      String updatedResponse =
          mvc.perform(
                  post(STIX_URI + "/process-bundle")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(stixSecurityCoverageWithoutObjects))
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
    @DisplayName(
        "Should create scenario with 1 injects with 3 assets when contract has no field asset group but asset")
    void shouldCreateScenarioWithOneInjectWithThreeEndpointsWhenContractHasNotAssetGroupField()
        throws Exception {
      String stixSecurityCoverageOnlyVulnsWithUpdatedLabel =
          stixSecurityCoverageOnlyVulns.replace("opencti", "coverage");

      String createdResponse =
          mvc.perform(
                  post(STIX_URI + "/process-bundle")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(stixSecurityCoverageOnlyVulnsWithUpdatedLabel))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String scenarioId = JsonPath.read(createdResponse, "$.scenarioId");
      Scenario createdScenario = scenarioRepository.findById(scenarioId).orElseThrow();
      assertThat(createdScenario.getName())
          .isEqualTo("Security Coverage Q3 2025 - Threat Report XYZ");

      Set<Inject> injects = injectRepository.findByScenarioId(createdScenario.getId());
      assertThat(injects).hasSize(1);
      Inject inject = injects.stream().findFirst().get();
      assertThat(inject.getAssets()).hasSize(3);
      assertThat(inject.getAssetGroups()).hasSize(0);
    }

    @Test
    @DisplayName(
        "Should create scenario with 1 injects with 1 asset group when contract has field asset group")
    void shouldCreateScenarioWithOneInjectWithOneAssetGroupWhenContractHasAssetGroupField()
        throws Exception {
      String stixSecurityCoverageOnlyVulnsWithUpdatedLabel =
          stixSecurityCoverageOnlyVulns
              .replace("opencti", "empty-asset-group")
              .replace("CVE-2025-56785", "CVE-2025-56786");

      String createdResponse =
          mvc.perform(
                  post(STIX_URI + "/process-bundle")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(stixSecurityCoverageOnlyVulnsWithUpdatedLabel))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String scenarioId = JsonPath.read(createdResponse, "$.scenarioId");
      Scenario createdScenario = scenarioRepository.findById(scenarioId).orElseThrow();
      assertThat(createdScenario.getName())
          .isEqualTo("Security Coverage Q3 2025 - Threat Report XYZ");

      Set<Inject> injects = injectRepository.findByScenarioId(createdScenario.getId());
      assertThat(injects).hasSize(1);
      Inject inject = injects.stream().findFirst().get();
      assertThat(inject.getAssets()).hasSize(0);
      assertThat(inject.getAssetGroups()).hasSize(1);
    }

    @Test
    @DisplayName(
        "Should create scenario with 1 inject for vulnerability when no asset group is present")
    void shouldCreateScenarioWithOneInjectWhenNoAssetGroupsExist() throws Exception {
      String stixSecurityCoverageOnlyVulnsWithUpdatedLabel =
          stixSecurityCoverageOnlyVulns.replace("opencti", "no-asset-groups");

      String createdResponse =
          mvc.perform(
                  post(STIX_URI + "/process-bundle")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(stixSecurityCoverageOnlyVulnsWithUpdatedLabel))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String scenarioId = JsonPath.read(createdResponse, "$.scenarioId");
      Scenario createdScenario = scenarioRepository.findById(scenarioId).orElseThrow();
      assertThat(createdScenario.getName())
          .isEqualTo("Security Coverage Q3 2025 - Threat Report XYZ");

      Set<Inject> injects = injectRepository.findByScenarioId(createdScenario.getId());
      assertThat(injects).hasSize(1);
      Inject inject = injects.stream().findFirst().get();
      assertThat(inject.getAssets()).hasSize(0);
      assertThat(inject.getAssetGroups()).hasSize(0);
    }

    @Test
    @DisplayName("Should create scenario with 1 inject when labels are no defined")
    void shouldCreateScenarioWithOneInjectWhenLabelsAreNotDefined() throws Exception {
      String createdResponse =
          mvc.perform(
                  post(STIX_URI + "/process-bundle")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(stixSecurityCoverageOnlyVulns))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String scenarioId = JsonPath.read(createdResponse, "$.scenarioId");
      Scenario createdScenario = scenarioRepository.findById(scenarioId).orElseThrow();
      assertThat(createdScenario.getName())
          .isEqualTo("Security Coverage Q3 2025 - Threat Report XYZ");

      Set<Inject> injects = injectRepository.findByScenarioId(createdScenario.getId());
      assertThat(injects).hasSize(1);
      Inject inject = injects.stream().findFirst().get();
      assertThat(inject.getAssets()).hasSize(0);
      assertThat(inject.getAssetGroups()).hasSize(0);
    }

    @Test
    @DisplayName("Should not update existing injects when some target is removed")
    void shouldNotUpdateInjectsWhenSomeTargetIsRemoved() throws Exception {
      String stixSecurityCoverageOnlyVulnsWithUpdatedLabel =
          stixSecurityCoverageOnlyVulns.replace("opencti", "coverage");

      String createdResponse =
          mvc.perform(
                  post(STIX_URI + "/process-bundle")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(stixSecurityCoverageOnlyVulnsWithUpdatedLabel))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String scenarioId = JsonPath.read(createdResponse, "$.scenarioId");
      Scenario scenario = scenarioRepository.findById(scenarioId).orElseThrow();
      assertThat(scenario.getName()).isEqualTo("Security Coverage Q3 2025 - Threat Report XYZ");

      Set<Inject> injects = injectRepository.findByScenarioId(scenario.getId());
      assertThat(injects).hasSize(1);
      assertThat(injects.stream().findFirst().get().getAssets()).hasSize(3);

      stixSecurityCoverageOnlyVulnsWithUpdatedLabel =
          stixSecurityCoverageOnlyVulns.replace("opencti", "empty-asset-groups");

      mvc.perform(
              post(STIX_URI + "/process-bundle")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(stixSecurityCoverageOnlyVulnsWithUpdatedLabel))
          .andExpect(status().isOk())
          .andReturn()
          .getResponse()
          .getContentAsString();

      scenario = scenarioRepository.findById(scenarioId).orElseThrow();
      injects = injectRepository.findByScenarioId(scenario.getId());
      assertThat(injects).hasSize(1);
      assertThat(injects.stream().findFirst().get().getAssets()).hasSize(3);
    }

    @Test
    @DisplayName("Should not update existing injects when more targets are added")
    void shouldNotUpdateInjectsWhenTargetsAreAdded() throws Exception {
      String stixSecurityCoverageOnlyVulnsWithUpdatedLabel =
          stixSecurityCoverageOnlyVulns.replace("opencti", "empty-asset-groups");

      String createdResponse =
          mvc.perform(
                  post(STIX_URI + "/process-bundle")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(stixSecurityCoverageOnlyVulnsWithUpdatedLabel))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String scenarioId = JsonPath.read(createdResponse, "$.scenarioId");
      Scenario scenario = scenarioRepository.findById(scenarioId).orElseThrow();
      Set<Inject> injects = injectRepository.findByScenarioId(scenario.getId());
      assertThat(injects).hasSize(1);

      Inject inject = injects.stream().findFirst().get();
      assertThat(inject.getAssets()).hasSize(0);

      stixSecurityCoverageOnlyVulnsWithUpdatedLabel =
          stixSecurityCoverageOnlyVulns.replace("opencti", "coverage");

      mvc.perform(
              post(STIX_URI + "/process-bundle")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(stixSecurityCoverageOnlyVulnsWithUpdatedLabel))
          .andExpect(status().isOk())
          .andReturn()
          .getResponse()
          .getContentAsString();

      scenario = scenarioRepository.findById(scenarioId).orElseThrow();
      injects = injectRepository.findByScenarioId(scenario.getId());
      assertThat(injects).hasSize(1);
      assertThat(
              injects.stream()
                  .filter(updated -> updated.getId().equals(inject.getId()))
                  .flatMap(i -> i.getAssets().stream())
                  .toList())
          .hasSize(0);
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
