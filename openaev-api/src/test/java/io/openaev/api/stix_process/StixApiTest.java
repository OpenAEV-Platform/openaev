package io.openaev.api.stix_process;

import static io.openaev.api.stix_process.StixApi.STIX_URI;
import static io.openaev.injector_contract.InjectorContractContentUtilsTest.createContentWithFieldAsset;
import static io.openaev.injector_contract.InjectorContractContentUtilsTest.createContentWithFieldAssetGroup;
import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openaev.rest.tag.TagService.OPENCTI_TAG_NAME;
import static io.openaev.utils.fixtures.VulnerabilityFixture.CVE_2023_48788;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.model.Tag;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.database.repository.SecurityCoverageRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.service.AssetGroupService;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.*;
import io.openaev.utils.fixtures.files.AttackPatternFixture;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.annotation.Resource;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@TestInstance(PER_CLASS)
@Transactional
@WithMockUser(withCapabilities = {Capability.MANAGE_STIX_BUNDLE})
@DisplayName("STIX API Integration Tests")
class StixApiTest extends IntegrationTest {

  public static final String T_1531 = "T1531";
  public static final String T_1003 = "T1003";

  @Resource protected ObjectMapper mapper;
  @Autowired private MockMvc mvc;
  @Autowired private EntityManager entityManager;

  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private TagRepository tagRepository;
  @Autowired private SecurityCoverageRepository securityCoverageRepository;
  @Autowired private AssetGroupService assetGroupService;

  @Autowired private AttackPatternComposer attackPatternComposer;
  @Autowired private VulnerabilityComposer vulnerabilityComposer;
  @Autowired private TagRuleComposer tagRuleComposer;
  @Autowired private AssetGroupComposer assetGroupComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private PayloadComposer payloadComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private TagComposer tagComposer;
  @Autowired private DomainComposer domainComposer;

  @Autowired private InjectorFixture injectorFixture;

  private String stixSecurityCoverage;
  private String stixSecurityCoverageNoLabels;
  private String stixSecurityCoverageWithoutTtps;
  private String stixSecurityCoverageWithoutVulns;
  private String stixSecurityCoverageWithoutObjects;
  private String stixSecurityCoverageOnlyVulns;
  private AssetGroupComposer.Composer completeAssetGroup;
  private AssetGroupComposer.Composer emptyAssetGroup;

  @BeforeEach
  void setUp() throws Exception {
    attackPatternComposer.reset();
    vulnerabilityComposer.reset();
    tagRuleComposer.reset();
    endpointComposer.reset();
    assetGroupComposer.reset();
    payloadComposer.reset();
    injectorContractComposer.reset();
    tagComposer.reset();

    stixSecurityCoverage =
        loadJsonWithStixObjectsAsText("src/test/resources/stix-bundles/security-coverage.json");

    stixSecurityCoverageNoLabels =
        loadJsonWithStixObjectsAsText(
            "src/test/resources/stix-bundles/security-coverage-no-labels.json");

    stixSecurityCoverageWithoutTtps =
        loadJsonWithStixObjectsAsText(
            "src/test/resources/stix-bundles/security-coverage-without-ttps.json");

    stixSecurityCoverageWithoutVulns =
        loadJsonWithStixObjectsAsText(
            "src/test/resources/stix-bundles/security-coverage-without-vulns.json");

    stixSecurityCoverageWithoutObjects =
        loadJsonWithStixObjectsAsText(
            "src/test/resources/stix-bundles/security-coverage-without-objects.json");

    stixSecurityCoverageOnlyVulns =
        loadJsonWithStixObjectsAsText(
            "src/test/resources/stix-bundles/security-coverage-only-vulns.json");

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
        .withInjector(injectorFixture.getWellKnownOaevImplantInjector())
        .withVulnerability(
            vulnerabilityComposer.forVulnerability(
                VulnerabilityFixture.createVulnerabilityInput("CVE-2025-56785")))
        .persist();

    injectorContractComposer
        .forInjectorContract(
            InjectorContractFixture.createInjectorContract(createContentWithFieldAssetGroup()))
        .withInjector(injectorFixture.getWellKnownOaevImplantInjector())
        .withVulnerability(
            vulnerabilityComposer.forVulnerability(
                VulnerabilityFixture.createVulnerabilityInput("CVE-2025-56786")))
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

  @Nested
  @DisplayName("Import STIX Bundles")
  class ImportStixBundles {

    @Test
    @DisplayName(
        "When Security Coverage SDO has no labels property, should force adding opencti tag to scenario")
    void whenSecurityCoverageSDOHasNoLabelsProperty_shouldForceAddingOpenctiTagToScenario()
        throws Exception {
      String response =
          mvc.perform(
                  post(STIX_URI + "/process-bundle")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(stixSecurityCoverageNoLabels))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThat(response).isNotBlank();
      String scenarioId = JsonPath.read(response, "$.scenarioId");
      Scenario createdScenario = scenarioRepository.findById(scenarioId).orElseThrow();
      Tag openctiTag = tagRepository.findByName(OPENCTI_TAG_NAME).get();

      assertThat(createdScenario.getTags()).contains(openctiTag);
    }

    @Test
    @DisplayName(
        "When Security Coverage SDO has labels property but not the opencti value, should force adding opencti tag to scenario")
    void
        whenSecurityCoverageSDOHasLabelsPropertyButNotTheOpenctiValue_shouldForceAddingOpenctiTagToScenario()
            throws Exception {
      String bundleWithoutOpenctiLabel = stixSecurityCoverage.replace("opencti", "some-label");

      String response =
          mvc.perform(
                  post(STIX_URI + "/process-bundle")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(bundleWithoutOpenctiLabel))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThat(response).isNotBlank();
      String scenarioId = JsonPath.read(response, "$.scenarioId");
      Scenario createdScenario = scenarioRepository.findById(scenarioId).orElseThrow();
      Tag openctiTag = tagRepository.findByName(OPENCTI_TAG_NAME).get();

      assertThat(createdScenario.getTags()).contains(openctiTag);
    }

    @Test
    @DisplayName("Eligible asset groups are assigned by tag rule")
    void eligibleAssetGroupsAreAssignedByTagRule() throws Exception {
      Set<Domain> domains = domainComposer.forDomain(null).persist().getSet();
      String label = "custom-label";
      tagRuleComposer
          .forTagRule(TagRuleFixture.createDefaultTagRule())
          .withTag(tagComposer.forTag(TagFixture.getTagWithText(label)))
          .withAssetGroup(
              assetGroupComposer
                  .forAssetGroup(
                      AssetGroupFixture.createDefaultAssetGroup("%s asset group".formatted(label)))
                  .withAsset(endpointComposer.forEndpoint(EndpointFixture.createEndpoint())))
          .persist();

      AttackPatternComposer.Composer attackPatternWrapper =
          attackPatternComposer.forAttackPattern(
              AttackPatternFixture.createAttackPatternsWithExternalId(T_1531));
      injectorContractComposer
          .forInjectorContract(
              InjectorContractFixture.createInjectorContractWithPlatforms(
                  List.of(Endpoint.PLATFORM_TYPE.Windows).toArray(Endpoint.PLATFORM_TYPE[]::new)))
          .withAttackPattern(attackPatternWrapper)
          .withPayload(
              payloadComposer
                  .forPayload(PayloadFixture.createDefaultCommand(domains))
                  .withAttackPattern(attackPatternWrapper))
          .persist();

      String bundleWithCustomLabel = stixSecurityCoverage.replace(OPENCTI_TAG_NAME, label);

      entityManager.flush();
      entityManager.clear();

      String response =
          mvc.perform(
                  post(STIX_URI + "/process-bundle")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(bundleWithCustomLabel))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      entityManager.flush();
      entityManager.clear();

      assertThat(response).isNotBlank();
      String scenarioId = JsonPath.read(response, "$.scenarioId");
      Scenario createdScenario = scenarioRepository.findById(scenarioId).orElseThrow();
      Tag customTag = tagRepository.findByName(label).get();

      assertThat(createdScenario.getTags()).contains(customTag);

      List<Inject> injects =
          createdScenario.getInjects().stream()
              .filter(i -> i.getInjectorContract().get().getPayload() != null)
              .toList();
      assertThat(injects).hasSize(1);

      Inject inject = injects.getFirst();
      Set<AssetGroup> desiredAssetGroups =
          assetGroupService.fetchAssetGroupsFromScenarioTagRules(createdScenario);
      assertThat(inject.getAssetGroups())
          .containsExactlyInAnyOrderElementsOf(desiredAssetGroups.stream().toList());
    }

    @Test
    @DisplayName("Should return 400 when STIX bundle has no security coverage")
    void shouldReturnBadRequestWhenNoSecurityCoverage() throws Exception {
      String bundleWithoutCoverage =
          stixSecurityCoverage.replace("security-coverage", "x-other-type");

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
      String invalidJson =
          """
                    {
                      "not-a-valid-json":
                    }
                    """;

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
          .isEqualTo("security-coverage--4c3b91e2-3b47-4f84-b2e6-d27e3f0581c1");
      assertTrue(createdScenario.getRecurrence().startsWith("0 "));
      assertTrue(createdScenario.getRecurrence().endsWith(" * * *"));
      assertThat(createdScenario.getTags().stream().map(Tag::getName).toList())
          .contains(OPENCTI_TAG_NAME);

      // -- ASSERT Security Coverage --
      assertThat(createdScenario.getSecurityCoverage().getAttackPatternRefs()).hasSize(3);

      StixRefToExternalRef stixRef1 =
          new StixRefToExternalRef("attack-pattern--a24d97e6-401c-51fc-be24-8f797a35d1f1", T_1531);
      StixRefToExternalRef stixRef2 =
          new StixRefToExternalRef("attack-pattern--033921be-85df-5f05-8bc0-d3d9fc945db9", T_1003);
      StixRefToExternalRef stixRef3 =
          new StixRefToExternalRef(
              "attack-pattern--c1fad538-bb66-4e3f-97f5-9a9a15fd34b1", "Attack!");

      // -- Vulnerabilities --
      assertThat(createdScenario.getSecurityCoverage().getVulnerabilitiesRefs()).hasSize(1);

      StixRefToExternalRef stixRefVuln =
          new StixRefToExternalRef(
              "vulnerability--de1172d3-a3e8-51a8-9014-30e572f3b975", CVE_2023_48788);

      assertTrue(
          createdScenario
              .getSecurityCoverage()
              .getAttackPatternRefs()
              .containsAll(List.of(stixRef1, stixRef2, stixRef3)));
      assertThat(createdScenario.getSecurityCoverage().getVulnerabilitiesRefs())
          .containsAll(List.of(stixRefVuln));
      assertThat(createdScenario.getSecurityCoverage().getContent()).isNotBlank();

      // -- ASSERT Injects --
      Set<Inject> injects = injectRepository.findByScenarioId(scenarioId);
      assertThat(injects).hasSize(4);
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
      assertThat(injects).hasSize(4);

      entityManager.flush();
      entityManager.clear();

      String modifiedSecurityCoverage =
          stixSecurityCoverage.replace("2025-08-04T14:00:00Z", "2025-12-20T14:00:00Z");

      // Push same stix in order to check the number of created injects
      String updatedResponse =
          mvc.perform(
                  post(STIX_URI + "/process-bundle")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(modifiedSecurityCoverage))
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
      assertThat(injects).hasSize(4);
    }

    @Test
    @DisplayName("Should throw bad request when security coverage is already saved")
    void shouldThrowBadRequestWhenSecurityCoverageIsAlreadySaved() throws Exception {
      mvc.perform(
              post(STIX_URI + "/process-bundle")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(stixSecurityCoverage))
          .andExpect(status().isOk());

      entityManager.flush();
      entityManager.clear();

      // Push same stix in order to check the number of created injects
      mvc.perform(
              post(STIX_URI + "/process-bundle")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(stixSecurityCoverage))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should throw bad request when security coverage is Obsolete")
    void shouldThrowBadRequestWhenSecurityCoverageIsObsolete() throws Exception {
      mvc.perform(
              post(STIX_URI + "/process-bundle")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(stixSecurityCoverage))
          .andExpect(status().isOk());

      entityManager.flush();
      entityManager.clear();

      String modifiedSecurityCoverage =
          stixSecurityCoverage.replace("2025-12-31T14:00:00Z", "2025-12-10T13:00:00Z");

      // Push an old Stix
      mvc.perform(
              post(STIX_URI + "/process-bundle")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(modifiedSecurityCoverage))
          .andExpect(status().isBadRequest());
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
      assertThat(injects).hasSize(4);

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
      assertThat(injects).hasSize(4);

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
      assertThat(injects).hasSize(4);

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
      assertThat(injects).isEmpty();
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
      assertThat(inject.getAssetGroups()).isEmpty();
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
      assertThat(inject.getAssets()).isEmpty();
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
      assertThat(inject.getAssets()).isEmpty();
      assertThat(inject.getAssetGroups()).isEmpty();
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
      assertThat(inject.getAssets()).isEmpty();
      assertThat(inject.getAssetGroups()).isEmpty();
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
      assertThat(inject.getAssets()).isEmpty();

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
          .isEmpty();
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
    @WithMockUser(withCapabilities = {Capability.MANAGE_STIX_BUNDLE, Capability.MANAGE_ASSESSMENT})
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

  private String loadJsonWithStixObjectsAsText(String filePath) throws IOException {
    String rawJson = IOUtils.toString(new FileInputStream(filePath), StandardCharsets.UTF_8);
    JsonNode rootNode = mapper.readTree(rawJson);

    JsonNode eventNode = rootNode.get("event");
    if (eventNode != null && eventNode.has("stix_objects")) {
      JsonNode stixObjectsNode = eventNode.get("stix_objects");

      if (!stixObjectsNode.isTextual()) {
        ((ObjectNode) eventNode).put("stix_objects", mapper.writeValueAsString(stixObjectsNode));
      }
    }

    return mapper.writeValueAsString(rootNode);
  }
}
