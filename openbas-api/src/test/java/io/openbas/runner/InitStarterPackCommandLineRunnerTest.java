package io.openbas.runner;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;

import io.openbas.IntegrationTest;
import io.openbas.database.model.*;
import io.openbas.database.model.Tag;
import io.openbas.database.repository.*;
import io.openbas.rest.tag.TagService;
import io.openbas.service.AssetGroupService;
import io.openbas.service.EndpointService;
import io.openbas.service.ImportService;
import io.openbas.service.ZipJsonService;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("StarterPack process tests")
public class InitStarterPackCommandLineRunnerTest extends IntegrationTest {

  @Autowired private TagRepository tagRepository;
  @Autowired private TagRuleRepository tagRuleRepository;
  @Autowired private AssetRepository assetRepository;
  @Autowired private EndpointRepository endpointRepository;
  @Autowired private AssetGroupRepository assetGroupRepository;
  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private CustomDashboardRepository customDashboardRepository;
  @Autowired private SettingRepository settingRepository;

  @Autowired private TagService tagService;
  @Autowired private EndpointService endpointService;
  @Autowired private AssetGroupService assetGroupService;
  @Autowired private ImportService importService;
  @Autowired private ZipJsonService<CustomDashboard> zipJsonService;
  @Autowired private ResourcePatternResolver resolver;
  @Mock private ImportService mockImportService;
  @Mock private ZipJsonService<CustomDashboard> mockZipJsonService;
  @Mock private ResourcePatternResolver mockResolver;

  @BeforeEach
  void beforeEach() {
    settingRepository.deleteAll();
    customDashboardRepository.deleteAll();
    scenarioRepository.deleteAll();
    assetGroupRepository.deleteAll();
    endpointRepository.deleteAll();
    assetRepository.deleteAll();
    tagRuleRepository.deleteAll();
    tagRepository.deleteAll();
  }

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
            importService,
            zipJsonService,
            resolver);
    ReflectionTestUtils.setField(initStarterPackCommandLineRunner, "isStarterPackEnabled", false);

    // EXECUTE
    initStarterPackCommandLineRunner.run();

    // VERIFY
    long tagCount = tagRepository.count();
    assertEquals(0, tagCount);

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
    assertEquals(0, tagCount);

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
            importService,
            mockZipJsonService,
            resolver);
    ReflectionTestUtils.setField(initStarterPackCommandLineRunner, "isStarterPackEnabled", true);
    doThrow(new IOException())
        .when(mockZipJsonService)
        .handleImport(any(), eq("custom_dashboard_name"), isNull());

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
  }

  private void verifyTagsExist() {
    long tagCount = tagRepository.count();
    assertEquals(2, tagCount);

    Optional<Tag> tagVulnerability = tagRepository.findByName("vulnerability");
    assertTrue(tagVulnerability.isPresent());

    Optional<Tag> tagCisco = tagRepository.findByName("cisco");
    assertTrue(tagCisco.isPresent());
  }

  private void verifyEndpointExist() {
    long assetsCount = assetRepository.count();
    assertEquals(1, assetsCount);

    Optional<Asset> assetHoneyScanMe = assetRepository.findByName("honey.scanme.sh");
    assertTrue(assetHoneyScanMe.isPresent());

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
    long assetGroupCount = assetGroupRepository.count();
    assertEquals(1, assetGroupCount);

    Optional<AssetGroup> assetGroupAllEndpoints = assetGroupRepository.findByName("All endpoints");
    assertTrue(assetGroupAllEndpoints.isPresent());
    assertNotNull(assetGroupAllEndpoints.get().getDynamicFilter());

    Filters.FilterGroup filterGroup = assetGroupAllEndpoints.get().getDynamicFilter();
    assertEquals(Filters.FilterMode.or, filterGroup.getMode());
    assertNotNull(filterGroup.getFilters());
    assertEquals(1, filterGroup.getFilters().size());

    Filters.Filter filter = filterGroup.getFilters().getFirst();
    assertEquals("endpoint_platform", filter.getKey());
    assertEquals(Filters.FilterOperator.not_empty, filter.getOperator());
    assertEquals(Filters.FilterMode.or, filter.getMode());
  }

  private void verifyScenarioExist() {
    long scenarioCount = scenarioRepository.count();
    assertEquals(1, scenarioCount);

    Optional<Scenario> scenario = scenarioRepository.findByName("starterpack (Import)");
    assertTrue(scenario.isPresent());
  }

  private void verifyDashboardExist() {
    long dashboardCount = customDashboardRepository.count();
    assertEquals(1, dashboardCount);

    Optional<CustomDashboard> dashboard = customDashboardRepository.findByName("test (Import)");
    assertTrue(dashboard.isPresent());
  }

  private void verifyParameterExist() {
    Optional<Setting> staticsParameters = settingRepository.findByKey("starterpack");
    assertTrue(staticsParameters.isPresent());
  }
}
