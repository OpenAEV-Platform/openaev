package io.openaev.api.catalog.scenario;

import static io.openaev.api.catalog.CatalogApi.TENANT_CATALOG_URI;
import static io.openaev.api.catalog.CatalogSettingsApi.CATALOG_SETTINGS_URI;
import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static io.openaev.rest.scenario.ScenarioApi.TENANT_SCENARIO_URI;
import static io.openaev.service.catalog.scenario.CatalogScenarioOfferService.CHAINED_REFUSED;
import static io.openaev.service.catalog.scenario.CatalogScenarioService.ALREADY_INSTALLED;
import static io.openaev.service.catalog.scenario.CatalogScenarioService.OTHER_PAYLOAD_REFUSED;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.PAYLOAD_ID;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.SCENARIO_ID;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.manifest;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.payloadFileName;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.payloadVersion;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.releasedPayload;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.scenarioFileName;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.scenarioItem;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.scenarioPackage;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.syncRequest;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.api.catalog.dto.CatalogSettingsInput;
import io.openaev.api.catalog.dto.scenario.CatalogScenarioInstallInput;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Payload;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.catalog.CatalogHistory;
import io.openaev.database.model.catalog.ContentType;
import io.openaev.database.repository.CatalogHistoryRepository;
import io.openaev.database.repository.EndpointRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.ScenarioIdentityRepository;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.service.catalog.scenario.CatalogScenarioInstaller;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.CatalogReleaseFixture.ReleasedPayload;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
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
import org.springframework.test.context.TestPropertySource;
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
  @Autowired private CatalogScenarioInstaller catalogScenarioInstaller;

  @BeforeEach
  void attachToDefaultTenant() {
    tenantHelper.attachCurrentUserToTenant(DEFAULT_TENANT_UUID);
    injectorFixture.getWellKnownOaevImplantInjector();
  }

  // -- HELPERS --

  /** Publishes the scenario, running the released payload, with what its manifest adds. */
  private void publish(Map<String, Object> manifestFields) throws Exception {
    byte[] archive = scenarioPackage(RELEASED.file());
    Map<String, Object> item = scenarioItem(VERSION, "VERIFIED", archive);
    item.put("payloads", List.of(Map.of("external_id", PAYLOAD_ID, "version", VERSION)));
    item.putAll(manifestFields);
    Map<String, byte[]> files = new LinkedHashMap<>();
    files.put(scenarioFileName(VERSION), archive);
    files.put(payloadFileName(VERSION), RELEASED.file());
    mvc.perform(
            syncRequest(
                    mapper.writeValueAsBytes(
                        manifest(
                            "2026.11.12",
                            List.of(item),
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

  private ResultActions install() throws Exception {
    return install(new CatalogScenarioInstallInput(null, null, null, null));
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
              install(new CatalogScenarioInstallInput(List.of(endpoint.getId()), null, null, null))
                  .andExpect(status().isCreated()));

      // Assert
      assertThat(injectsOf(scenario).getFirst().getAssets())
          .extracting(asset -> asset.getId())
          .containsExactly(endpoint.getId());
    }
  }

  @Nested
  @TestInstance(PER_CLASS)
  @DisplayName("Refusing")
  class Refusing {

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
          Arguments.of(Map.of("type", "chained"), CHAINED_REFUSED),
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
}
