package io.openaev.runner;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;

import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.model.Tag;
import io.openaev.database.repository.*;
import io.openaev.rest.tag.TagService;
import io.openaev.service.*;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("StarterPack process tests")
@Transactional
public class InitStarterPackCommandLineRunnerTest extends IntegrationTest {

  @Autowired private TagRepository tagRepository;
  @Autowired private AssetRepository assetRepository;
  @Autowired private EndpointRepository endpointRepository;
  @Autowired private AssetGroupRepository assetGroupRepository;
  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private CustomDashboardRepository customDashboardRepository;
  @Autowired private SettingRepository settingRepository;

  @Autowired private TagService tagService;
  @Autowired private EndpointService endpointService;
  @Autowired private AssetGroupService assetGroupService;
  @Autowired private TagRuleService tagRuleService;
  @Autowired private ImportService importService;
  @Autowired private ZipJsonService<CustomDashboard> zipJsonService;
  @Autowired private ResourcePatternResolver resolver;
  @Mock private ImportService mockImportService;
  @Mock private ZipJsonService<CustomDashboard> mockZipJsonService;
  @Mock private ResourcePatternResolver mockResolver;

  @Test
  @DisplayName("Should not init StarterPack for disabled feature")
  public void shouldNotInitStarterPackForDisabledFeature() {
    // PREPARE
    InitStarterPackCommandLineRunner initStarterPackCommandLineRunner =
        new InitStarterPackCommandLineRunner(
            settingRepository,
            tagService,
            endpointService,
            assetGroupService,
            tagRuleService,
            importService,
            zipJsonService,
            resolver);
    ReflectionTestUtils.setField(initStarterPackCommandLineRunner, "isStarterPackEnabled", false);

    // EXECUTE
    initStarterPackCommandLineRunner.run();

    // VERIFY
    long tagCount = tagRepository.count();
    assertEquals(1, tagCount); // 1 by default, because OpenCTI tag is created by other process

    long assetsCount = assetRepository.count();
    assertEquals(0, assetsCount);

    long assetGroupCount = assetGroupRepository.count();
    assertEquals(0, assetGroupCount);

    long scenarioCount = scenarioRepository.count();
    assertEquals(0, scenarioCount);

    long dashboardCount = customDashboardRepository.count();
    assertEquals(0, dashboardCount);

    Optional<Setting> staticsParameters = settingRepository.findByKey("starterpack");
    assertFalse(staticsParameters.isPresent());
  }

  @Test
  @DisplayName("Should not init StarterPack if already integrated")
  public void shouldNotInitStarterPackIfAlreadyIntegrated() {
    // PREPARE
    InitStarterPackCommandLineRunner initStarterPackCommandLineRunner =
        new InitStarterPackCommandLineRunner(
            settingRepository,
            tagService,
            endpointService,
            assetGroupService,
            tagRuleService,
            importService,
            zipJsonService,
            resolver);
    ReflectionTestUtils.setField(initStarterPackCommandLineRunner, "isStarterPackEnabled", true);
    Setting setting = new Setting();
    setting.setKey("starterpack");
    setting.setValue("Mock StarterPack integration");
    settingRepository.save(setting);

    // EXECUTE
    initStarterPackCommandLineRunner.run();

    // VERIFY
    long tagCount = tagRepository.count();
    assertEquals(1, tagCount); // 1 by default, because OpenCTI tag is created by other process

    long assetsCount = assetRepository.count();
    assertEquals(0, assetsCount);

    long assetGroupCount = assetGroupRepository.count();
    assertEquals(0, assetGroupCount);

    long scenarioCount = scenarioRepository.count();
    assertEquals(0, scenarioCount);

    long dashboardCount = customDashboardRepository.count();
    assertEquals(0, dashboardCount);

    Optional<Setting> staticsParameters = settingRepository.findByKey("starterpack");
    assertTrue(staticsParameters.isPresent());
  }

  @Test
  @DisplayName("Should not init StarterPack Scenarios for import failure")
  public void shouldNotInitStarterPackScenariosForImportFailure() throws Exception {
    // PREPARE
    InitStarterPackCommandLineRunner initStarterPackCommandLineRunner =
        new InitStarterPackCommandLineRunner(
            settingRepository,
            tagService,
            endpointService,
            assetGroupService,
            tagRuleService,
            mockImportService,
            zipJsonService,
            resolver);
    ReflectionTestUtils.setField(initStarterPackCommandLineRunner, "isStarterPackEnabled", true);
    doThrow(new Exception()).when(mockImportService).handleFileImport(any(), isNull(), isNull());

    // EXECUTE
    initStarterPackCommandLineRunner.run();

    // VERIFY
    this.verifyTagsExist();
    this.verifyEndpointExist();
    this.verifyAssetGroupExist();
    long scenarioCount = scenarioRepository.count();
    assertEquals(0, scenarioCount);
    this.verifyDashboardExist();
    this.verifyParameterExist();
    this.verifyDefaultHomeDashboardParameterExist();
    this.verifyDefaultScenarioDashboardParameterExist();
    this.verifyDefaultSimulationDashboardParameterExist();
  }

  @Test
  @DisplayName("Should not init StarterPack Dashboards for import failure")
  public void shouldNotInitStarterPackDashboardsForImportFailure() throws Exception {
    // PREPARE
    InitStarterPackCommandLineRunner initStarterPackCommandLineRunner =
        new InitStarterPackCommandLineRunner(
            settingRepository,
            tagService,
            endpointService,
            assetGroupService,
            tagRuleService,
            importService,
            mockZipJsonService,
            resolver);
    ReflectionTestUtils.setField(initStarterPackCommandLineRunner, "isStarterPackEnabled", true);
    doThrow(new IOException())
        .when(mockZipJsonService)
        .handleImport(any(), eq("custom_dashboard_name"), isNull(), isNull());

    // EXECUTE
    initStarterPackCommandLineRunner.run();

    // VERIFY
    this.verifyTagsExist();
    this.verifyEndpointExist();
    this.verifyAssetGroupExist();
    this.verifyScenarioExist();
    long dashboardCount = customDashboardRepository.count();
    assertEquals(0, dashboardCount);
    this.verifyParameterExist();
  }

  @Test
  @DisplayName("Should not init StarterPack Scenarios and Dashboards for import failure")
  public void shouldNotInitStarterPackScenariosAndDashboardsForImportFailure() throws Exception {
    // PREPARE
    InitStarterPackCommandLineRunner initStarterPackCommandLineRunner =
        new InitStarterPackCommandLineRunner(
            settingRepository,
            tagService,
            endpointService,
            assetGroupService,
            tagRuleService,
            importService,
            zipJsonService,
            mockResolver);
    ReflectionTestUtils.setField(initStarterPackCommandLineRunner, "isStarterPackEnabled", true);
    doThrow(new IOException())
        .when(mockResolver)
        .getResources(eq("classpath:starterpack/scenarios/*"));
    doThrow(new IOException())
        .when(mockResolver)
        .getResources(eq("classpath:starterpack/dashboards/*"));

    // EXECUTE
    initStarterPackCommandLineRunner.run();

    // VERIFY
    this.verifyTagsExist();
    this.verifyEndpointExist();
    this.verifyAssetGroupExist();
    long scenarioCount = scenarioRepository.count();
    assertEquals(0, scenarioCount);
    long dashboardCount = customDashboardRepository.count();
    assertEquals(0, dashboardCount);
    this.verifyParameterExist();
  }

  @Test
  @DisplayName("Should init StarterPack")
  public void shouldInitStarterPack() {
    // PREPARE
    InitStarterPackCommandLineRunner initStarterPackCommandLineRunner =
        new InitStarterPackCommandLineRunner(
            settingRepository,
            tagService,
            endpointService,
            assetGroupService,
            tagRuleService,
            importService,
            zipJsonService,
            resolver);
    ReflectionTestUtils.setField(initStarterPackCommandLineRunner, "isStarterPackEnabled", true);

    // EXECUTE
    initStarterPackCommandLineRunner.run();

    // VERIFY
    this.verifyTagsExist();
    this.verifyEndpointExist();
    this.verifyAssetGroupExist();
    this.verifyScenarioExist();
    this.verifyDashboardExist();
    this.verifyParameterExist();
    this.verifyDefaultHomeDashboardParameterExist();
    this.verifyDefaultScenarioDashboardParameterExist();
    this.verifyDefaultSimulationDashboardParameterExist();
  }

  private void verifyTagsExist() {
    long tagCount = tagRepository.count();
    assertEquals(3, tagCount);

    Optional<Tag> tagVulnerability = tagRepository.findByName("vulnerability");
    assertTrue(tagVulnerability.isPresent());

    Optional<Tag> tagCisco = tagRepository.findByName("cisco");
    assertTrue(tagCisco.isPresent());

    Optional<Tag> tagOpenCTI = tagRepository.findByName("opencti");
    assertTrue(tagOpenCTI.isPresent());
  }

  private void verifyEndpointExist() {
    List<Asset> assets =
        StreamSupport.stream(assetRepository.findAll().spliterator(), false).toList();
    assertEquals(1, assets.size());

    Asset assetHoneyScanMe = assets.getFirst();
    assertEquals("honey.scanme.sh", assetHoneyScanMe.getName());

    List<Endpoint> endpoints =
        endpointRepository.findByHostnameAndAtleastOneIp(
            "honey.scanme.sh", new String[] {"67.205.158.113"});
    assertNotNull(endpoints);
    assertEquals(1, endpoints.size());

    Endpoint honeyScanMeEndpoint = endpoints.getFirst();
    assertEquals("honey.scanme.sh", honeyScanMeEndpoint.getName());
    assertEquals(Endpoint.PLATFORM_ARCH.x86_64, honeyScanMeEndpoint.getArch());
    assertEquals(Endpoint.PLATFORM_TYPE.Generic, honeyScanMeEndpoint.getPlatform());
    assertTrue(honeyScanMeEndpoint.isEoL());
  }

  private void verifyAssetGroupExist() {
    List<AssetGroup> assetGroups =
        StreamSupport.stream(assetGroupRepository.findAll().spliterator(), false).toList();
    assertEquals(1, assetGroups.size());

    AssetGroup assetGroupAllEndpoints = assetGroups.getFirst();
    assertEquals("All endpoints", assetGroupAllEndpoints.getName());
    assertNotNull(assetGroupAllEndpoints.getDynamicFilter());

    Filters.FilterGroup filterGroup = assetGroupAllEndpoints.getDynamicFilter();
    assertEquals(Filters.FilterMode.or, filterGroup.getMode());
    assertNotNull(filterGroup.getFilters());
    assertEquals(1, filterGroup.getFilters().size());

    Filters.Filter filter = filterGroup.getFilters().getFirst();
    assertEquals("endpoint_platform", filter.getKey());
    assertEquals(Filters.FilterOperator.not_empty, filter.getOperator());
    assertEquals(Filters.FilterMode.or, filter.getMode());
  }

  private void verifyScenarioExist() {
    List<Scenario> scenarios = scenarioRepository.findAll();
    assertEquals(1, scenarios.size());

    Scenario scenario = scenarios.getFirst();
    assertEquals("starterpack (Import)", scenario.getName());
  }

  private void verifyDashboardExist() {
    long dashboardCount = customDashboardRepository.count();
    assertEquals(3, dashboardCount);

    Optional<CustomDashboard> dashboardTest =
        customDashboardRepository.findByName("Test 1 (Import)");
    assertTrue(dashboardTest.isPresent());

    Optional<CustomDashboard> dashboardTest2 =
        customDashboardRepository.findByName("Test 2 (Import)");
    assertTrue(dashboardTest2.isPresent());

    Optional<CustomDashboard> dashboardTest3 =
        customDashboardRepository.findByName("Test 3 (Import)");
    assertTrue(dashboardTest3.isPresent());
  }

  private void verifyParameterExist() {
    Optional<Setting> staticsParameters = settingRepository.findByKey("starterpack");
    assertTrue(staticsParameters.isPresent());
  }

  private void verifyDefaultHomeDashboardParameterExist() {
    Optional<CustomDashboard> dashboardTest =
        customDashboardRepository.findByName("Test 1 (Import)");
    assertTrue(dashboardTest.isPresent());

    Optional<Setting> staticsParameters = settingRepository.findByKey("platform_home_dashboard");
    assertTrue(staticsParameters.isPresent());
    assertEquals(dashboardTest.get().getId(), staticsParameters.get().getValue());
  }

  private void verifyDefaultScenarioDashboardParameterExist() {
    Optional<CustomDashboard> dashboardTest =
        customDashboardRepository.findByName("Test 2 (Import)");
    assertTrue(dashboardTest.isPresent());

    Optional<Setting> staticsParameters =
        settingRepository.findByKey("platform_scenario_dashboard");
    assertTrue(staticsParameters.isPresent());
    assertEquals(dashboardTest.get().getId(), staticsParameters.get().getValue());
  }

  private void verifyDefaultSimulationDashboardParameterExist() {
    Optional<CustomDashboard> dashboardTest =
        customDashboardRepository.findByName("Test 3 (Import)");
    assertTrue(dashboardTest.isPresent());

    Optional<Setting> staticsParameters =
        settingRepository.findByKey("platform_simulation_dashboard");
    assertTrue(staticsParameters.isPresent());
    assertEquals(dashboardTest.get().getId(), staticsParameters.get().getValue());
  }
}
