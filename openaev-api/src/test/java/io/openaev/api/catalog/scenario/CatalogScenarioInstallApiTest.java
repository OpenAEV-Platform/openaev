package io.openaev.api.catalog.scenario;

import static io.openaev.api.catalog.CatalogRoutes.TENANT_CATALOG_URI;
import static io.openaev.api.catalog.CatalogSettingsApi.CATALOG_SETTINGS_URI;
import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static io.openaev.rest.scenario.ScenarioApi.TENANT_SCENARIO_URI;
import static io.openaev.service.catalog.scenario.CatalogScenarioService.ALREADY_INSTALLED;
import static io.openaev.service.catalog.scenario.CatalogScenarioService.OTHER_PAYLOAD_REFUSED;
import static io.openaev.service.catalog.scenario.CatalogScenarioService.OTHER_TYPE_CHOICE_REFUSED;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.PAYLOAD_ID;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.SCENARIO_ID;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.chainedScenarioPackage;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.manifest;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.payloadFileName;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.payloadVersion;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.releasedPayload;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.scenarioFileName;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.scenarioItem;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.scenarioPackage;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.syncRequest;
import static io.openaev.utils.fixtures.MarkingDefinitionFixture.createDefaultMarkingDefinition;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.api.catalog.dto.CatalogSettingsInput;
import io.openaev.api.catalog.dto.scenario.CatalogScenarioInstallInput;
import io.openaev.api.catalog.dto.scenario.ChainedScenarioInstallInput;
import io.openaev.api.catalog.dto.scenario.TimeBasedScenarioInstallInput;
import io.openaev.api.chaining.dto.WorkflowConfigurationInput;
import io.openaev.api.scenario.dto.ScenarioTargetsInput;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Payload;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowStatus;
import io.openaev.database.model.catalog.CatalogHistory;
import io.openaev.database.model.catalog.ContentType;
import io.openaev.database.repository.CatalogHistoryRepository;
import io.openaev.database.repository.EndpointRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.ScenarioIdentityRepository;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.database.repository.WorkflowRepository;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.service.catalog.scenario.CatalogScenarioInstaller;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.CatalogReleaseFixture.ReleasedPayload;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.composers.MarkingDefinitionComposer;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@TestPropertySource(properties = "openaev.enabled-dev-features=MARKING,CATALOG")
@DisplayName("Catalogue scenario install")
class CatalogScenarioInstallApiTest extends IntegrationTest {

  private static final ReleasedPayload RELEASED = releasedPayload();
  private static final String VERSION = "1.0.0";

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private InjectorFixture injectorFixture;
  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private ScenarioIdentityRepository scenarioIdentityRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private EndpointRepository endpointRepository;
  @Autowired private CatalogHistoryRepository catalogHistoryRepository;
  @Autowired private WorkflowRepository workflowRepository;
  // Chained scenarios need the Enterprise Edition: a mock never finds the license inactive.
  @MockitoBean private EnterpriseEditionService enterpriseEditionService;
  @Autowired private CatalogScenarioInstaller catalogScenarioInstaller;
  @Autowired private MarkingDefinitionComposer markingDefinitionComposer;
  @Autowired private DataSource dataSource;

  @BeforeEach
  void attachToDefaultTenant() {
    markingDefinitionComposer.reset();
    tenantHelper.attachCurrentUserToTenant(DEFAULT_TENANT_UUID);
    injectorFixture.getWellKnownOaevImplantInjector();
  }

  // -- HELPERS --

  /** Publishes the scenario, running the released payload, with what its manifest adds. */
  private void publish(Map<String, Object> manifestFields) throws Exception {
    publish(manifestFields, scenarioPackage(RELEASED.file()));
  }

  private void publish(Map<String, Object> manifestFields, byte[] archive) throws Exception {
    publishVersions(Map.of(VERSION, manifestFields), archive);
  }

  /** Publishes several versions of the scenario, each with what its manifest adds. */
  private void publishVersions(Map<String, Map<String, Object>> fieldsByVersion, byte[] archive)
      throws Exception {
    List<Map<String, Object>> items = new ArrayList<>();
    Map<String, byte[]> files = new LinkedHashMap<>();
    fieldsByVersion.forEach(
        (version, fields) -> {
          Map<String, Object> item = scenarioItem(version, "VERIFIED", archive);
          item.put("payloads", List.of(Map.of("external_id", PAYLOAD_ID, "version", VERSION)));
          item.putAll(fields);
          items.add(item);
          files.put(scenarioFileName(version), archive);
        });
    files.put(payloadFileName(VERSION), RELEASED.file());
    mvc.perform(
            syncRequest(
                    mapper.writeValueAsBytes(
                        manifest(
                            "2026.11.12",
                            items,
                            List.of(payloadVersion(VERSION, "VERIFIED", RELEASED)))),
                    files,
                    TENANT_CATALOG_URI + "/sync",
                    DEFAULT_TENANT_UUID)
                .with(csrf()))
        .andExpect(status().isOk());
  }

  // The test transaction never commits: flush what a request changed, as its commit would.
  private ResultActions committed(MockHttpServletRequestBuilder request) throws Exception {
    ResultActions result = mvc.perform(request.with(csrf()));
    entityManager.flush();
    return result;
  }

  private ResultActions install(CatalogScenarioInstallInput targets) throws Exception {
    return committed(
        post(
                TENANT_CATALOG_URI + "/scenarios/{externalId}/versions/{version}/install",
                DEFAULT_TENANT_UUID,
                SCENARIO_ID,
                VERSION)
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(targets)));
  }

  private static TimeBasedScenarioInstallInput timeBased(List<String> assetIds) {
    return new TimeBasedScenarioInstallInput(new ScenarioTargetsInput(assetIds, null, null, null));
  }

  private ResultActions install() throws Exception {
    return install(timeBased(null));
  }

  private Scenario installedScenario(ResultActions result) throws Exception {
    String id =
        mapper
            .readTree(result.andReturn().getResponse().getContentAsString())
            .path("installed_id")
            .asText();
    return scenarioRepository.findById(id).orElseThrow();
  }

  // The delete comes in a request of its own, with a session of its own.
  private void uninstall(Scenario scenario) throws Exception {
    entityManager.clear();
    committed(delete(TENANT_SCENARIO_URI + "/{scenarioId}", DEFAULT_TENANT_UUID, scenario.getId()))
        .andExpect(status().isOk());
    entityManager.clear();
  }

  private List<Inject> injectsOf(Scenario scenario) {
    return List.copyOf(injectRepository.findByScenarioId(scenario.getId()));
  }

  private List<CatalogHistory> history() {
    entityManager.flush();
    return catalogHistoryRepository.findAll();
  }

  // -- INSTALL --

  @Nested
  @DisplayName("Installing")
  class Installing {

    @Test
    @DisplayName("installs the scenario with the payload version its release names")
    void given_releasedScenario_should_installItRunningItsPayloadVersion() throws Exception {
      // Arrange
      publish(Map.of());

      // Act
      Scenario scenario = installedScenario(install().andExpect(status().isCreated()));

      // Assert
      assertThat(scenario.getCatalogExternalId()).isEqualTo(SCENARIO_ID);
      assertThat(scenario.getCatalogVersion()).isEqualTo(VERSION);
      Payload payload =
          injectsOf(scenario).getFirst().getInjectorContract().orElseThrow().getPayload();
      assertThat(payload.getExternalId()).isEqualTo(PAYLOAD_ID);
      assertThat(payload.getVersion()).isEqualTo(VERSION);
      assertThat(history())
          .extracting(CatalogHistory::getType, CatalogHistory::getAction)
          .containsExactlyInAnyOrder(
              tuple(ContentType.PAYLOAD, CatalogHistory.Action.INSTALL),
              tuple(ContentType.SCENARIO, CatalogHistory.Action.INSTALL));
    }

    @Test
    @DisplayName("adds the chosen assets to the injects that take assets")
    void given_chosenAsset_should_targetIt() throws Exception {
      // Arrange
      publish(Map.of());
      Endpoint endpoint = endpointRepository.save(EndpointFixture.createEndpoint());

      // Act
      Scenario scenario =
          installedScenario(
              install(timeBased(List.of(endpoint.getId()))).andExpect(status().isCreated()));

      // Assert
      assertThat(injectsOf(scenario).getFirst().getAssets())
          .extracting(asset -> asset.getId())
          .containsExactly(endpoint.getId());
    }
  }

  @Nested
  @DisplayName("Installing a chained scenario")
  class InstallingChained {

    @Test
    @DisplayName(
        "installs it with the limits the admin chose, its step running the released payload")
    void given_chainedScenario_should_installItWithTheChosenLimits() throws Exception {
      // Arrange
      publish(Map.of("type", "chained"), chainedScenarioPackage(RELEASED.file()));
      WorkflowConfigurationInput limits =
          WorkflowConfigurationInput.builder().timeoutEnabled(true).timeoutSeconds(600L).build();

      // Act
      Scenario scenario =
          installedScenario(
              install(new ChainedScenarioInstallInput(limits)).andExpect(status().isCreated()));

      // Assert
      Workflow workflow =
          workflowRepository
              .findByScenario_IdAndStatus(scenario.getId(), WorkflowStatus.TEMPLATE)
              .getFirst();
      assertThat(workflow.isTimeoutEnabled()).isTrue();
      assertThat(workflow.getTimeoutSeconds()).isEqualTo(600L);
    }
  }

  @Nested
  @TestInstance(PER_CLASS)
  @DisplayName("Refusing")
  class Refusing {

    @Test
    @DisplayName("refuses the scope and limits of a chained scenario for a time-based one")
    void given_choiceOfAnotherType_should_refuseTheInstall() throws Exception {
      // Arrange
      publish(Map.of());

      // Act & Assert
      install(new ChainedScenarioInstallInput(null))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value(OTHER_TYPE_CHOICE_REFUSED));
    }

    @Test
    @DisplayName("refuses a scenario already installed")
    void given_installedScenario_should_refuseInstallingItAgain() throws Exception {
      // Arrange
      publish(Map.of());
      install().andExpect(status().isCreated());

      // Act & Assert
      install()
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value(ALREADY_INSTALLED));
    }

    static Stream<Arguments> unfitReleases() {
      return Stream.of(
          Arguments.of(
              Map.of("requires", Map.of("injectors", List.of("openaev_unknown"))),
              "Missing to install this scenario: Injector openaev_unknown"),
          Arguments.of(Map.of("payloads", List.of()), OTHER_PAYLOAD_REFUSED));
    }

    @ParameterizedTest
    @MethodSource("unfitReleases")
    @DisplayName("refuses a scenario the platform cannot run, or running a payload not released")
    void given_unfitRelease_should_refuseTheInstall(
        Map<String, Object> manifestFields, String message) throws Exception {
      // Arrange
      publish(manifestFields);

      // Act & Assert
      install().andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(message));
    }
  }

  @Nested
  @TestInstance(PER_CLASS)
  @DisplayName("Installing every scenario never installed")
  class InstallingAll {

    static Stream<Arguments> releases() {
      return Stream.of(
          Arguments.of("a scenario never installed", Map.of(), false, 1),
          Arguments.of("a scenario already installed", Map.of(), true, 0),
          Arguments.of(
              "a chained scenario, left to install by hand", Map.of("type", "chained"), false, 0));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("releases")
    @DisplayName("installs the latest version of each scenario never installed")
    void given_release_should_installOnlyTheScenariosNeverInstalled(
        String release, Map<String, Object> manifestFields, boolean installedBefore, int installed)
        throws Exception {
      // Arrange
      publish(manifestFields);
      if (installedBefore) {
        install().andExpect(status().isCreated());
      }

      // Act
      String response =
          committed(post(TENANT_CATALOG_URI + "/scenarios/install-all", DEFAULT_TENANT_UUID))
              .andExpect(status().isCreated())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      assertThatJson(response).node("installed_ids").isArray().hasSize(installed);
      assertThatJson(response).node("failed_external_ids").isArray().isEmpty();
    }
  }

  @Nested
  @DisplayName("Installing by itself after a release")
  class AutoInstalling {

    @ParameterizedTest(name = "auto-install {0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("installs the scenarios never installed only when the tenant asks for it")
    void given_autoInstallSetting_should_installNewScenariosOnlyWhenOn(boolean autoInstall)
        throws Exception {
      // Arrange
      publish(Map.of());
      committed(
              put(CATALOG_SETTINGS_URI, DEFAULT_TENANT_UUID)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(mapper.writeValueAsString(new CatalogSettingsInput(true, autoInstall))))
          .andExpect(status().isOk());

      // Act
      catalogScenarioInstaller.autoInstall(
          TxCtx.forTenant(DEFAULT_TENANT_UUID), DEFAULT_TENANT_UUID);

      // Assert
      assertThat(history())
          .filteredOn(row -> row.getType() == ContentType.SCENARIO)
          .extracting(CatalogHistory::getAction, CatalogHistory::getMode)
          .containsExactlyElementsOf(
              autoInstall
                  ? List.of(tuple(CatalogHistory.Action.INSTALL, CatalogHistory.Mode.AUTO))
                  : List.of());
    }

    @Test
    @DisplayName("leaves out a scenario the tenant uninstalled")
    void given_uninstalledScenario_should_notInstallItAgain() throws Exception {
      // Arrange
      publish(Map.of());
      uninstall(installedScenario(install().andExpect(status().isCreated())));

      // Act
      catalogScenarioInstaller.autoInstall(
          TxCtx.forTenant(DEFAULT_TENANT_UUID), DEFAULT_TENANT_UUID);

      // Assert
      assertThat(history())
          .filteredOn(row -> row.getType() == ContentType.SCENARIO)
          .extracting(CatalogHistory::getAction)
          .containsExactly(CatalogHistory.Action.INSTALL, CatalogHistory.Action.UNINSTALL);
    }
  }

  // -- BROWSE --

  @Nested
  @TestInstance(PER_CLASS)
  @DisplayName("Browsing the catalogue")
  class Browsing {

    private ResultActions available() throws Exception {
      return mvc.perform(
          post(TENANT_CATALOG_URI + "/scenarios/search", DEFAULT_TENANT_UUID)
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content(mapper.writeValueAsString(new SearchPaginationInput())));
    }

    static Stream<Arguments> scenarioStates() {
      return Stream.of(
          Arguments.of("never installed", false, false),
          Arguments.of("installed", true, false),
          Arguments.of("uninstalled", true, true));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarioStates")
    @DisplayName("lists a scenario with the one the tenant installed from it, if any")
    void given_scenarioState_should_listItWithTheScenarioInstalled(
        String description, boolean installed, boolean uninstalled) throws Exception {
      // Arrange
      publish(Map.of());
      Scenario scenario = null;
      if (installed) {
        scenario = installedScenario(install().andExpect(status().isCreated()));
        if (uninstalled) {
          uninstall(scenario);
        }
      }

      // Act
      ResultActions result = available();

      // Assert
      result.andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1));
      if (installed && !uninstalled) {
        result.andExpect(
            jsonPath("$.content[0].catalog_scenario_installed_scenario_id")
                .value(scenario.getId()));
      } else {
        result.andExpect(
            jsonPath("$.content[0].catalog_scenario_installed_scenario_id").doesNotExist());
      }
    }

    @Test
    @DisplayName("names what the platform lacks to install a scenario")
    void given_missingInjector_should_listItAsMissing() throws Exception {
      // Arrange
      publish(Map.of("requires", Map.of("injectors", List.of("openaev_unknown"))));

      // Act
      ResultActions result = available();

      // Assert
      result
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content[0].catalog_scenario_external_id").value(SCENARIO_ID))
          .andExpect(
              jsonPath("$.content[0].catalog_scenario_missing_requirements[0]")
                  .value("Injector openaev_unknown"));
    }

    @Test
    @DisplayName("lists the offered versions of a scenario with their details, newest first")
    void given_releasedVersions_should_listOfferedOnesNewestFirst() throws Exception {
      // Arrange
      publishVersions(
          Map.of(
              "1.0.0", Map.of(),
              "1.2.0",
                  Map.of(
                      "changelog", "Adds a discovery step",
                      "main_focus", "incident-response",
                      "severity", "high",
                      "injects_count", 3),
              "1.1.0", Map.of(),
              "1.3.0", Map.of("status", "DEPRECATED")),
          scenarioPackage(RELEASED.file()));

      // Act
      ResultActions result =
          mvc.perform(
              get(
                  TENANT_CATALOG_URI + "/scenarios/{externalId}/versions",
                  DEFAULT_TENANT_UUID,
                  SCENARIO_ID));

      // Assert
      result
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[*].catalog_scenario_version", contains("1.2.0", "1.1.0", "1.0.0")))
          .andExpect(jsonPath("$[0].catalog_scenario_changelog").value("Adds a discovery step"))
          .andExpect(jsonPath("$[0].catalog_scenario_main_focus").value("incident-response"))
          .andExpect(jsonPath("$[0].catalog_scenario_severity").value("high"))
          .andExpect(jsonPath("$[0].catalog_scenario_injects_count").value(3));
    }
  }

  // -- LAUNCH --

  @Nested
  @DisplayName("Launching with targets")
  class Launching {

    private String launch(Scenario scenario, String... assetIds) throws Exception {
      return mapper
          .readTree(
              committed(
                      post(
                              TENANT_SCENARIO_URI + "/{scenarioId}/exercise/running",
                              DEFAULT_TENANT_UUID,
                              scenario.getId())
                          .contentType(MediaType.APPLICATION_JSON)
                          .content(
                              mapper.writeValueAsString(
                                  new ScenarioTargetsInput(List.of(assetIds), null, null, null))))
                  .andExpect(status().isOk())
                  .andReturn()
                  .getResponse()
                  .getContentAsString())
          .path("simulation_id")
          .asText();
    }

    @Test
    @DisplayName("adds the chosen assets to the simulation injects only, the scenario unchanged")
    void given_installedScenario_when_launchedWithTargets_should_targetOnlyTheSimulation()
        throws Exception {
      // Arrange
      publish(Map.of());
      Endpoint first = endpointRepository.save(EndpointFixture.createEndpoint());
      Endpoint second = endpointRepository.save(EndpointFixture.createEndpoint());
      Scenario scenario =
          installedScenario(
              install(timeBased(List.of(first.getId()))).andExpect(status().isCreated()));

      // Act
      String simulationId = launch(scenario, second.getId());

      // Assert
      assertThat(injectRepository.findByExerciseId(simulationId).getFirst().getAssets())
          .extracting(asset -> asset.getId())
          .containsExactlyInAnyOrder(first.getId(), second.getId());
      assertThat(injectsOf(scenario).getFirst().getAssets())
          .extracting(asset -> asset.getId())
          .containsExactly(first.getId());
    }

    @Test
    @DisplayName("drops a chosen asset the user is not cleared for, as an unknown one")
    void given_assetHiddenFromUser_when_chosenAtLaunch_should_notTargetIt() throws Exception {
      // Arrange
      publish(Map.of());
      Endpoint visible = endpointRepository.save(EndpointFixture.createEndpoint());
      Endpoint hidden = endpointRepository.save(EndpointFixture.createEndpoint());
      Scenario scenario = installedScenario(install().andExpect(status().isCreated()));
      String marking =
          markingDefinitionComposer
              .forMarkingDefinition(createDefaultMarkingDefinition())
              .withTenantId(DEFAULT_TENANT_UUID)
              .persist()
              .get()
              .getId();
      entityManager.flush();
      // Raw JDBC: an ORM write of a marking the user does not hold is refused.
      JdbcTemplate jdbc = new JdbcTemplate(dataSource);
      jdbc.update(
          "UPDATE assets SET marking_ids = ? WHERE asset_id = ?",
          new String[] {marking},
          hidden.getId());
      entityManager.clear();

      // Act
      String simulationId = launch(scenario, visible.getId(), hidden.getId());

      // Assert
      assertThat(
              jdbc.queryForList(
                  "SELECT ia.asset_id FROM injects_assets ia"
                      + " JOIN injects i ON i.inject_id = ia.inject_id WHERE i.inject_exercise = ?",
                  String.class,
                  simulationId))
          .containsExactly(visible.getId());
    }
  }

  // -- DELETE --

  @Nested
  @DisplayName("Uninstalling")
  class Uninstalling {

    @Test
    @DisplayName("deleting an installed scenario uninstalls it, and it can be installed again")
    void given_installedScenario_when_deleted_should_uninstallItAndKeepItsIdentity()
        throws Exception {
      // Arrange
      publish(Map.of());
      Scenario scenario = installedScenario(install().andExpect(status().isCreated()));

      // Act
      uninstall(scenario);
      Scenario reinstalled = installedScenario(install().andExpect(status().isCreated()));

      // Assert
      assertThat(history())
          .filteredOn(row -> row.getType() == ContentType.SCENARIO)
          .extracting(CatalogHistory::getAction)
          .containsExactly(
              CatalogHistory.Action.INSTALL,
              CatalogHistory.Action.UNINSTALL,
              CatalogHistory.Action.INSTALL);
      assertThat(scenarioIdentityRepository.findAll())
          .singleElement()
          .satisfies(
              identity -> {
                assertThat(identity.getExternalId()).isEqualTo(SCENARIO_ID);
                assertThat(identity.getId()).isEqualTo(reinstalled.getCatalogIdentity().getId());
              });
    }
  }

  // -- HISTORY --

  @Nested
  @DisplayName("History")
  class History {

    @Test
    @DisplayName("lists the installs and uninstalls of the scenario, newest first")
    void given_reinstalledScenario_should_listItsHistoryNewestFirst() throws Exception {
      // Arrange
      publish(Map.of());
      uninstall(installedScenario(install().andExpect(status().isCreated())));
      Scenario reinstalled = installedScenario(install().andExpect(status().isCreated()));

      // Act & Assert
      committed(
              get(
                  TENANT_SCENARIO_URI + "/{scenarioId}/history",
                  DEFAULT_TENANT_UUID,
                  reinstalled.getId()))
          .andExpect(status().isOk())
          .andExpect(
              jsonPath("$[*].catalog_history_action")
                  .value(contains("INSTALL", "UNINSTALL", "INSTALL")))
          .andExpect(jsonPath("$[*].catalog_history_version").value(everyItem(is(VERSION))));
    }

    @Test
    @DisplayName("shows the installed catalogue version on the scenario")
    void given_installedScenario_should_showItsCatalogueVersion() throws Exception {
      // Arrange
      publish(Map.of());
      Scenario scenario = installedScenario(install().andExpect(status().isCreated()));

      // Act & Assert
      committed(get(TENANT_SCENARIO_URI + "/{scenarioId}", DEFAULT_TENANT_UUID, scenario.getId()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.scenario_version").value(VERSION));
    }
  }
}
