package io.openaev.runner;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.model.Tag;
import io.openaev.database.repository.*;
import io.openaev.injector_contract.ContractCardinality;
import io.openaev.injector_contract.fields.ContractAsset;
import io.openaev.injector_contract.fields.ContractAssetGroup;
import io.openaev.rest.tag.TagService;
import io.openaev.service.*;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.PayloadFixture;
import io.openaev.utils.fixtures.composers.DomainComposer;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.fixtures.composers.PayloadComposer;
import io.openaev.utilstest.RabbitMQTestListener;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
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
  @Autowired private TagRuleRepository tagRuleRepository;

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

  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private DomainComposer domainComposer;
  @Autowired private PayloadComposer payloadComposer;
  @Autowired private InjectRepository injectRepository;

  @Test
  @DisplayName("Should not init StarterPack for disabled feature")
  public void shouldNotInitStarterPackForDisabledFeature() {
    // PREPARE
    InitStarterPackCommandLineRunner initStarterPackCommandLineRunner =
        new InitStarterPackCommandLineRunner(
            settingRepository,
            tagRuleRepository,
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
            tagRuleRepository,
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
            tagRuleRepository,
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
    this.verifyTagRuleExist();
  }

  @Test
  @DisplayName("Should not init StarterPack Dashboards for import failure")
  public void shouldNotInitStarterPackDashboardsForImportFailure() throws Exception {
    // PREPARE
    InitStarterPackCommandLineRunner initStarterPackCommandLineRunner =
        new InitStarterPackCommandLineRunner(
            settingRepository,
            tagRuleRepository,
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
        .handleImport(any(), eq("custom_dashboard_name"), isNull(), isNull(), eq(""));

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
            tagRuleRepository,
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
            tagRuleRepository,
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
    this.verifyTagRuleExist();
  }

  @Test
  @DisplayName("Should init StarterPack even if OpenCTI tag rule doesn't exist")
  public void shouldInitStarterPackEvenIfOpenCTITagRuleDoesntExist() {
    // PREPARE
    Optional<Tag> openCtiTag = this.tagRepository.findByName("opencti");
    assertFalse(openCtiTag.isEmpty());
    List<TagRule> tagRules = this.tagRuleRepository.findByTags(List.of(openCtiTag.get().getId()));
    tagRules.forEach(tagRule -> this.tagRuleRepository.deleteById(tagRule.getId()));

    InitStarterPackCommandLineRunner initStarterPackCommandLineRunner =
        new InitStarterPackCommandLineRunner(
            settingRepository,
            tagRuleRepository,
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
    this.verifyTagRuleExist();
  }

  @Test
  @DisplayName("Should init StarterPack with honey.scan.me asset")
  public void shouldInitStarterPackWithDefaultAssets() throws JsonProcessingException {
    // PREPARE
    Set<Domain> domains = domainComposer.forDomain(null).persist().getSet();

    ContractAsset contractAsset = new ContractAsset(ContractCardinality.Multiple);
    contractAsset.setLinkedFields(InjectorContractFixture.buildMandatoryOnConditionValue("assets"));
    Injector injector = InjectorFixture.createDefaultPayloadInjector();
    Payload payload = PayloadFixture.createDefaultCommand(domains);
    InjectorContract injectorContract =
        InjectorContractFixture.createPayloadInjectorContractWithFieldsContent(
            injector, payload, List.of(contractAsset));
    // Be careful should match inject into the zip scenario
    injectorContract.setId("2e7fc079-4444-4531-4444-928fe4a1fc0b");
    injectorContractComposer
        .forInjectorContract(injectorContract)
        .withInjector(injector)
        .withPayload(payloadComposer.forPayload(payload))
        .persist();

    InitStarterPackCommandLineRunner initStarterPackCommandLineRunner =
        new InitStarterPackCommandLineRunner(
            settingRepository,
            tagRuleRepository,
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
    this.verifyTagRuleExist();

    List<Inject> injects = this.injectRepository.findAll();
    assertFalse(injects.isEmpty());
    assertTrue(
        injects.stream()
            .anyMatch(
                inject ->
                    inject.getAssets() != null
                        && "honey.scanme.sh".equals(inject.getAssets().getFirst().getName())));
  }

  @Test
  @DisplayName("Should init StarterPack with All endpoints asset group")
  public void shouldInitStarterPackWithDefaultAssetGroups() throws JsonProcessingException {
    // PREPARE
    Set<Domain> domains = domainComposer.forDomain(null).persist().getSet();

    ContractAssetGroup contractAssetGroup = new ContractAssetGroup(ContractCardinality.Multiple);
    contractAssetGroup.setLinkedFields(
        InjectorContractFixture.buildMandatoryOnConditionValue("asset_groups"));
    Injector injector = InjectorFixture.createDefaultPayloadInjector();
    Payload payload = PayloadFixture.createDefaultCommand(domains);
    InjectorContract injectorContract =
        InjectorContractFixture.createPayloadInjectorContractWithFieldsContent(
            injector, payload, List.of(contractAssetGroup));
    // Be careful should match inject into the zip scenario
    injectorContract.setId("df0d6fe6-ffb1-4e4c-a5f8-11a45b30dd69");
    injectorContractComposer
        .forInjectorContract(injectorContract)
        .withInjector(injector)
        .withPayload(payloadComposer.forPayload(payload))
        .persist();

    InitStarterPackCommandLineRunner initStarterPackCommandLineRunner =
        new InitStarterPackCommandLineRunner(
            settingRepository,
            tagRuleRepository,
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
    this.verifyTagRuleExist();

    List<Inject> injects = this.injectRepository.findAll();
    assertFalse(injects.isEmpty());
    assertTrue(
        injects.stream()
            .anyMatch(
                inject ->
                    inject.getAssetGroups() != null
                        && !inject.getAssetGroups().isEmpty()
                        && "All endpoints".equals(inject.getAssetGroups().getFirst().getName())));
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
    assertEquals(2, scenarios.size());

    Scenario scenario = scenarios.getFirst();
    assertEquals("starterpack", scenario.getName());
  }

  private void verifyDashboardExist() {
    long dashboardCount = customDashboardRepository.count();
    assertEquals(3, dashboardCount);

    Optional<CustomDashboard> dashboardTest = customDashboardRepository.findByName("Test 1");
    assertTrue(dashboardTest.isPresent());

    Optional<CustomDashboard> dashboardTest2 = customDashboardRepository.findByName("Test 2");
    assertTrue(dashboardTest2.isPresent());

    Optional<CustomDashboard> dashboardTest3 = customDashboardRepository.findByName("Test 3");
    assertTrue(dashboardTest3.isPresent());
  }

  private void verifyParameterExist() {
    Optional<Setting> staticsParameters = settingRepository.findByKey("starterpack");
    assertTrue(staticsParameters.isPresent());
  }

  private void verifyDefaultHomeDashboardParameterExist() {
    Optional<CustomDashboard> dashboardTest = customDashboardRepository.findByName("Test 1");
    assertTrue(dashboardTest.isPresent());

    Optional<Setting> staticsParameters = settingRepository.findByKey("platform_home_dashboard");
    assertTrue(staticsParameters.isPresent());
    assertEquals(dashboardTest.get().getId(), staticsParameters.get().getValue());
  }

  private void verifyDefaultScenarioDashboardParameterExist() {
    Optional<CustomDashboard> dashboardTest = customDashboardRepository.findByName("Test 2");
    assertTrue(dashboardTest.isPresent());

    Optional<Setting> staticsParameters =
        settingRepository.findByKey("platform_scenario_dashboard");
    assertTrue(staticsParameters.isPresent());
    assertEquals(dashboardTest.get().getId(), staticsParameters.get().getValue());
  }

  private void verifyDefaultSimulationDashboardParameterExist() {
    Optional<CustomDashboard> dashboardTest = customDashboardRepository.findByName("Test 3");
    assertTrue(dashboardTest.isPresent());

    Optional<Setting> staticsParameters =
        settingRepository.findByKey("platform_simulation_dashboard");
    assertTrue(staticsParameters.isPresent());
    assertEquals(dashboardTest.get().getId(), staticsParameters.get().getValue());
  }

  private void verifyTagRuleExist() {
    Optional<TagRule> tagRule = this.tagRuleRepository.findTagRuleByTagName("opencti");
    assertTrue(tagRule.isPresent());
  }
}
