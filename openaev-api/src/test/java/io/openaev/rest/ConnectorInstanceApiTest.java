package io.openaev.rest;

import static io.openaev.database.model.SettingKeys.*;
import static io.openaev.database.model.SettingKeys.XTM_COMPOSER_LAST_CONNECTIVITY_CHECK;
import static io.openaev.rest.connector_instance.ConnectorInstanceApi.CONNECTOR_INSTANCE_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.CatalogConnectorFixture.*;
import static io.openaev.utils.fixtures.ConnectorInstanceFixture.*;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.elastic.clients.util.TriConsumer;
import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.repository.ConnectorInstanceConfigurationRepository;
import io.openaev.database.repository.ConnectorInstanceLogRepository;
import io.openaev.database.repository.ConnectorInstanceRepository;
import io.openaev.database.repository.TokenRepository;
import io.openaev.ee.Ee;
import io.openaev.rest.connector_instance.dto.CreateConnectorInstanceInput;
import io.openaev.rest.connector_instance.dto.UpdateConnectorInstanceRequestedStatus;
import io.openaev.service.PlatformSettingsService;
import io.openaev.service.connector_instances.XtmComposerEncryptionService;
import io.openaev.utils.fixtures.composers.CatalogConnectorComposer;
import io.openaev.utils.fixtures.composers.CatalogConnectorConfigurationComposer;
import io.openaev.utils.fixtures.composers.ConnectorInstanceComposer;
import io.openaev.utils.fixtures.composers.ConnectorInstanceConfigurationComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@TestInstance(PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Connector Instance API Integration Tests")
public class ConnectorInstanceApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;

  @Autowired private ConnectorInstanceRepository connectorInstanceRepository;
  @Autowired private ConnectorInstanceLogRepository connectorInstanceLogRepository;

  @Autowired
  private ConnectorInstanceConfigurationRepository connectorInstanceConfigurationRepository;

  @MockBean private TokenRepository tokenRepository;

  @Autowired private PlatformSettingsService platformSettingsService;
  @MockBean private Ee eeService;
  @MockBean private XtmComposerEncryptionService xtmComposerEncryptionService;

  @Autowired private CatalogConnectorComposer catalogConnectorComposer;
  @Autowired private CatalogConnectorConfigurationComposer catalogConfigurationComposer;
  @Autowired private ConnectorInstanceComposer connectorInstanceComposer;
  @Autowired private ConnectorInstanceConfigurationComposer connectorInstanceConfigurationComposer;

  private ConnectorInstancePersisted getConnectorInstance(
      CatalogConnector catalogConnector, Set<ConnectorInstanceConfiguration> configurationsValues) {
    ConnectorInstanceComposer.Composer builder =
        connectorInstanceComposer
            .forConnectorInstance(createDefaultConnectorInstance())
            .withCatalogConnector(catalogConnectorComposer.forCatalogConnector(catalogConnector));
    for (ConnectorInstanceConfiguration configValue : configurationsValues) {
      builder =
          builder.withConnectorInstanceConfiguration(
              connectorInstanceConfigurationComposer.forConnectorInstanceConfiguration(
                  configValue));
    }
    return builder.persist().get();
  }

  private CatalogConnector getCatalogConnector() {
    return catalogConnectorComposer
        .forCatalogConnector(createDefaultCatalogConnectorManagedByXtmComposer("New Collector"))
        .persist()
        .get();
  }

  private CatalogConnector getCatalogConnectorWithConfiguration(
      Set<CatalogConnectorConfiguration> configurationsDefinition) {
    CatalogConnectorComposer.Composer builder =
        catalogConnectorComposer.forCatalogConnector(
            createDefaultCatalogConnectorManagedByXtmComposer("New Collector"));
    for (CatalogConnectorConfiguration configDef : configurationsDefinition) {
      builder =
          builder.withCatalogConnectorConfiguration(
              catalogConfigurationComposer.forCatalogConnectorConfiguration(configDef));
    }
    return builder.persist().get();
  }

  @Nested
  @DisplayName("Create connector instance")
  class CreateConnectorInstanceTests {
    @Test
    @DisplayName("Given no enterprise edition license should throw an error")
    void givenNoEnterpriseEditionLicense_should_throwError() throws Exception {
      CatalogConnector catalogConnector = getCatalogConnector();
      CreateConnectorInstanceInput input = new CreateConnectorInstanceInput();
      input.setCatalogConnectorId(catalogConnector.getId());
      mvc.perform(
              post(CONNECTOR_INSTANCE_URI)
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.message").value("LICENSE_RESTRICTION"));
    }

    @Test
    @DisplayName("Given connector supported by manager should throw an error when xtmComposer down")
    void givenConnectorSupportedByManager_should_throwErrorIfXtmComposerDown() throws Exception {
      CatalogConnector catalogConnector = getCatalogConnector();
      when(eeService.isLicenseActive(any())).thenReturn(true);

      CreateConnectorInstanceInput input = new CreateConnectorInstanceInput();
      input.setCatalogConnectorId(catalogConnector.getId());
      mvc.perform(
              post(CONNECTOR_INSTANCE_URI)
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isBadRequest())
          .andExpect(
              result -> {
                String errorMessage = result.getResolvedException().getMessage();
                assertTrue(
                    errorMessage.contains(
                        "XTM Composer is not configured in the platform settings"));
              });

      Map<String, String> composerSettings = new HashMap<>();
      composerSettings.put(XTM_COMPOSER_ID.key(), "composer-id-test");
      composerSettings.put(XTM_COMPOSER_VERSION.key(), "composer-version-test");
      composerSettings.put(
          XTM_COMPOSER_LAST_CONNECTIVITY_CHECK.key(),
          Instant.now().minus(3, ChronoUnit.HOURS).toString());
      platformSettingsService.saveSettings(composerSettings);
      mvc.perform(
              post(CONNECTOR_INSTANCE_URI)
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isBadRequest())
          .andExpect(
              result -> {
                String errorMessage = result.getResolvedException().getMessage();
                assertTrue(errorMessage.contains("XTM Composer is not reachable"));
              });
    }

    @Test
    @DisplayName("Duplicate catalog connector instance id should throw an error")
    void duplicateCatalogConnectorInstance_should_throwError() throws Exception {
      when(eeService.isLicenseActive(any())).thenReturn(true);
      CatalogConnector catalogConnector = getCatalogConnector();
      ConnectorInstancePersisted instance = createDefaultConnectorInstance();
      instance.setCatalogConnector(catalogConnector);
      instance.setConfigurations(new HashSet<>());
      connectorInstanceRepository.save(instance);

      Map<String, String> composerSettings = new HashMap<>();
      composerSettings.put(XTM_COMPOSER_ID.key(), "composer-id-test");
      composerSettings.put(XTM_COMPOSER_VERSION.key(), "composer-version-test");
      composerSettings.put(XTM_COMPOSER_LAST_CONNECTIVITY_CHECK.key(), Instant.now().toString());
      platformSettingsService.saveSettings(composerSettings);

      CreateConnectorInstanceInput input = new CreateConnectorInstanceInput();
      input.setCatalogConnectorId(catalogConnector.getId());

      mvc.perform(
              post(CONNECTOR_INSTANCE_URI)
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().is4xxClientError())
          .andExpect(
              result -> {
                String errorMessage = result.getResolvedException().getMessage();
                assertTrue(
                    errorMessage.contains(
                        "ConnectorInstance with CatalogConnector id "
                            + catalogConnector.getId()
                            + " already exists"));
              });
    }

    @Test
    @DisplayName("Should successfully create a connector instance from a catalog connector")
    void should_successfullyCreateConnectorInstance() throws Exception {
      when(eeService.isLicenseActive(any())).thenReturn(true);
      when(xtmComposerEncryptionService.encrypt(any())).thenReturn("fake-encrypted-value");
      Token token = new Token();
      token.setValue("fake-token-value");
      when(tokenRepository.findAll(any())).thenReturn(List.of(token));

      Set<String> enumList = Set.of("info", "debug", "warn");
      CatalogConnectorConfiguration confDef1 =
          createCatalogConfiguration(
              "key-string",
              CatalogConnectorConfiguration.CONNECTOR_CONFIGURATION_TYPE.STRING,
              true,
              null,
              null,
              null);
      CatalogConnectorConfiguration confDef2 =
          createCatalogConfiguration(
              "key-enum",
              CatalogConnectorConfiguration.CONNECTOR_CONFIGURATION_TYPE.STRING,
              false,
              null,
              enumList,
              null);
      CatalogConnectorConfiguration confDef3 =
          createCatalogConfiguration(
              "key-password",
              CatalogConnectorConfiguration.CONNECTOR_CONFIGURATION_TYPE.STRING,
              true,
              null,
              null,
              CatalogConnectorConfiguration.CONNECTOR_CONFIGURATION_FORMAT.PASSWORD);
      CatalogConnector catalogConnector =
          getCatalogConnectorWithConfiguration(Set.of(confDef1, confDef2, confDef3));

      Map<String, String> composerSettings = new HashMap<>();
      composerSettings.put(XTM_COMPOSER_ID.key(), "composer-id-test");
      composerSettings.put(XTM_COMPOSER_VERSION.key(), "composer-version-test");
      composerSettings.put(XTM_COMPOSER_LAST_CONNECTIVITY_CHECK.key(), Instant.now().toString());
      platformSettingsService.saveSettings(composerSettings);

      CreateConnectorInstanceInput input = new CreateConnectorInstanceInput();
      input.setCatalogConnectorId(catalogConnector.getId());
      CreateConnectorInstanceInput.ConfigurationInput confInput1 =
          createConfigurationInput(confDef1.getConnectorConfigurationKey(), "value-string");
      CreateConnectorInstanceInput.ConfigurationInput confInput2 =
          createConfigurationInput(confDef2.getConnectorConfigurationKey(), "debug");
      CreateConnectorInstanceInput.ConfigurationInput confInput3 =
          createConfigurationInput(confDef3.getConnectorConfigurationKey(), "secret-password");
      input.setConfigurations(List.of(confInput1, confInput2, confInput3));

      mvc.perform(
              post(CONNECTOR_INSTANCE_URI)
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().is2xxSuccessful())
          .andReturn()
          .getResponse()
          .getContentAsString();

      List<ConnectorInstancePersisted> instanceDb =
          connectorInstanceRepository.findAllByCatalogConnectorId(catalogConnector.getId());
      assertEquals(1, instanceDb.size());
      assertEquals(
          ConnectorInstance.CURRENT_STATUS_TYPE.stopped, instanceDb.getFirst().getCurrentStatus());
      assertEquals(
          ConnectorInstance.REQUESTED_STATUS_TYPE.stopping,
          instanceDb.getFirst().getRequestedStatus());
      assertEquals(ConnectorInstance.SOURCE.CATALOG_DEPLOYMENT, instanceDb.getFirst().getSource());
      assertEquals(
          5,
          instanceDb.getFirst().getConfigurations().size()); // 3 from input + token + collector_id
      Set<ConnectorInstanceConfiguration> configurations =
          instanceDb.getFirst().getConfigurations();
      TriConsumer<String, String, Boolean> assertConfiguration =
          (String key, String expectedValue, Boolean expectedIsEncrypted) -> {
            Optional<ConnectorInstanceConfiguration> confValue =
                configurations.stream().filter(c -> key.equals(c.getKey())).findFirst();
            assertTrue(confValue.isPresent());
            assertEquals(expectedValue, confValue.get().getValue().asText());
            if (expectedIsEncrypted) {
              assertTrue(confValue.get().isEncrypted());
            } else {
              assertFalse(confValue.get().isEncrypted());
            }
          };
      // Test configuration from input
      assertConfiguration.accept(confDef1.getConnectorConfigurationKey(), "value-string", false);
      assertConfiguration.accept(confDef2.getConnectorConfigurationKey(), "debug", false);
      assertConfiguration.accept(
          confDef3.getConnectorConfigurationKey(), "fake-encrypted-value", true);
      assertConfiguration.accept("OPENAEV_TOKEN", "fake-token-value", false);

      Optional<ConnectorInstanceConfiguration> confValueCollectorId =
          configurations.stream().filter(c -> "COLLECTOR_ID".equals(c.getKey())).findFirst();
      assertTrue(confValueCollectorId.isPresent());
      assertFalse(confValueCollectorId.get().getValue().asText().isEmpty());
      assertFalse(confValueCollectorId.get().isEncrypted());
    }
  }

  @Test
  @DisplayName("Given id should retrieve connector instance associated")
  void givenId_shouldRetrievedConnectorInstance() throws Exception {
    CatalogConnector catalogConnector = getCatalogConnector();
    getConnectorInstance(catalogConnector, new HashSet<>());
    ConnectorInstance instance2 = getConnectorInstance(catalogConnector, new HashSet<>());
    String response =
        mvc.perform(
                get(CONNECTOR_INSTANCE_URI + "/" + instance2.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThatJson(response).inPath("$.connector_instance_id").isEqualTo(instance2.getId());
    assertThatJson(response)
        .inPath("$.connector_instance_current_status")
        .isEqualTo(instance2.getCurrentStatus());
    assertThatJson(response)
        .inPath("$.connector_instance_requested_status")
        .isEqualTo(instance2.getRequestedStatus());
  }

  @Test
  @DisplayName(
      "Given connector instance id should retrieve all connector instance configurations associated")
  void givenConnectorInstanceId_shouldRetrievedAllConnectorInstanceConfigurations()
      throws Exception {
    ConnectorInstanceConfiguration confValue1 =
        createConnectorInstanceConfiguration("key1", "value1");
    ConnectorInstanceConfiguration confValue2 =
        createConnectorInstanceConfiguration("key2", "value2");
    ConnectorInstanceConfiguration confValue3 =
        createConnectorInstanceConfiguration("key3", "value3");
    CatalogConnector catalogConnector = getCatalogConnector();
    ConnectorInstance instance =
        getConnectorInstance(catalogConnector, Set.of(confValue1, confValue3));
    connectorInstanceConfigurationRepository.save(confValue2);

    String response =
        mvc.perform(
                get(CONNECTOR_INSTANCE_URI + "/" + instance.getId() + "/configurations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThatJson(response).isArray().size().isEqualTo(2);
    assertThatJson(response)
        .inPath("[*].connector_instance_configuration_key")
        .isArray()
        .containsExactlyInAnyOrderElementsOf(List.of("key3", "key1"));
    assertThatJson(response)
        .inPath("[*].connector_instance_configuration_value")
        .isArray()
        .containsExactlyInAnyOrderElementsOf(List.of("value3", "value1"));
  }

  @Test
  @DisplayName("Given connector instance id should not retrieve secrets")
  void givenConnectorInstanceId_shouldNotRetrieveSecrets() throws Exception {
    ConnectorInstanceConfiguration confValue1 =
        createConnectorInstanceConfiguration("key1", "value1");
    ConnectorInstanceConfiguration secretConf =
        createConnectorInstanceSecretConfiguration("key_secret", "secret!");
    CatalogConnector catalogConnector = getCatalogConnector();
    ConnectorInstance instance =
        getConnectorInstance(catalogConnector, Set.of(confValue1, secretConf));

    String response =
        mvc.perform(
                get(CONNECTOR_INSTANCE_URI + "/" + instance.getId() + "/configurations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThatJson(response).isArray().size().isEqualTo(1);
    assertThatJson(response)
        .inPath("[*].connector_instance_configuration_key")
        .isArray()
        .containsExactlyInAnyOrderElementsOf(List.of("key1"));
    assertThatJson(response)
        .inPath("[*].connector_instance_configuration_value")
        .isArray()
        .containsExactlyInAnyOrderElementsOf(List.of("value1"));
  }

  @Nested
  @DisplayName("Update connector instance configuration")
  class UpdateConnectorInstanceConfigurations {
    @Test
    @DisplayName("Given no enterprise edition license should throw an error")
    void givenNoEnterpriseEditionLicense_should_throwError() throws Exception {
      CatalogConnector catalogConnector = getCatalogConnector();
      CreateConnectorInstanceInput input = new CreateConnectorInstanceInput();
      input.setCatalogConnectorId(catalogConnector.getId());
      mvc.perform(
              put(CONNECTOR_INSTANCE_URI + "/fake-instance-id/configurations")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.message").value("LICENSE_RESTRICTION"));
    }

    @Test
    @DisplayName("Given connector supported by manager should throw an error when xtmComposer down")
    void givenConnectorSupportedByManager_should_throwErrorIfXtmComposerDown() throws Exception {
      CatalogConnector catalogConnector = getCatalogConnector();
      ConnectorInstance instance = getConnectorInstance(catalogConnector, new HashSet<>());
      when(eeService.isLicenseActive(any())).thenReturn(true);

      CreateConnectorInstanceInput input = new CreateConnectorInstanceInput();
      input.setCatalogConnectorId(catalogConnector.getId());
      mvc.perform(
              put(CONNECTOR_INSTANCE_URI + "/" + instance.getId() + "/configurations")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isBadRequest())
          .andExpect(
              result -> {
                String errorMessage = result.getResolvedException().getMessage();
                assertTrue(
                    errorMessage.contains(
                        "XTM Composer is not configured in the platform settings"));
              });

      Map<String, String> composerSettings = new HashMap<>();
      composerSettings.put(XTM_COMPOSER_ID.key(), "composer-id-test");
      composerSettings.put(XTM_COMPOSER_VERSION.key(), "composer-version-test");
      composerSettings.put(
          XTM_COMPOSER_LAST_CONNECTIVITY_CHECK.key(),
          Instant.now().minus(3, ChronoUnit.HOURS).toString());
      platformSettingsService.saveSettings(composerSettings);
      mvc.perform(
              put(CONNECTOR_INSTANCE_URI + "/" + instance.getId() + "/configurations")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isBadRequest())
          .andExpect(
              result -> {
                String errorMessage = result.getResolvedException().getMessage();
                assertTrue(errorMessage.contains("XTM Composer is not reachable"));
              });
    }

    @Test
    @DisplayName(
        "Should successfully update connector instance configuration and remove old configurations")
    void shouldSuccessfullyUpdateConnectorInstanceConfiguration() throws Exception {
      when(eeService.isLicenseActive(any())).thenReturn(true);

      CatalogConnectorConfiguration confDef1 =
          createCatalogConfiguration(
              "key-string-01",
              CatalogConnectorConfiguration.CONNECTOR_CONFIGURATION_TYPE.STRING,
              true,
              null,
              null,
              null);
      CatalogConnectorConfiguration confDef2 =
          createCatalogConfiguration(
              "key-string-02",
              CatalogConnectorConfiguration.CONNECTOR_CONFIGURATION_TYPE.STRING,
              true,
              null,
              null,
              null);

      CatalogConnector catalogConnector =
          getCatalogConnectorWithConfiguration(Set.of(confDef1, confDef2));
      ConnectorInstancePersisted connectorInstance =
          getConnectorInstance(
              catalogConnector,
              Set.of(createConnectorInstanceConfiguration("key-string-01", "old value 01")));

      Map<String, String> composerSettings = new HashMap<>();
      composerSettings.put(XTM_COMPOSER_ID.key(), "composer-id-test");
      composerSettings.put(XTM_COMPOSER_VERSION.key(), "composer-version-test");
      composerSettings.put(XTM_COMPOSER_LAST_CONNECTIVITY_CHECK.key(), Instant.now().toString());
      platformSettingsService.saveSettings(composerSettings);

      CreateConnectorInstanceInput input = new CreateConnectorInstanceInput();
      input.setCatalogConnectorId(connectorInstance.getCatalogConnector().getId());
      CreateConnectorInstanceInput.ConfigurationInput confInput1 =
          createConfigurationInput("key-string-01", "new value 01");
      CreateConnectorInstanceInput.ConfigurationInput confInput2 =
          createConfigurationInput("key-string-02", "new value 02");
      input.setConfigurations(List.of(confInput1, confInput2));

      mvc.perform(
              put(CONNECTOR_INSTANCE_URI + "/" + connectorInstance.getId() + "/configurations")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().is2xxSuccessful())
          .andReturn()
          .getResponse()
          .getContentAsString();

      List<ConnectorInstanceConfiguration> confSaved =
          connectorInstanceConfigurationRepository.findByConnectorInstanceId(
              connectorInstance.getId());
      assertEquals(2, confSaved.size());
      assertTrue(
          confSaved.stream()
              .map(ConnectorInstanceConfiguration::getKey)
              .toList()
              .containsAll(
                  List.of(
                      confDef1.getConnectorConfigurationKey(),
                      confDef2.getConnectorConfigurationKey())));
      assertTrue(
          confSaved.stream()
              .map(c -> c.getValue().asText())
              .toList()
              .containsAll(List.of("new value 02", "new value 01")));
    }
  }

  @Nested
  @DisplayName("Update connector instance requested status")
  class UpdateInstanceRequestedStatus {

    @Test
    @DisplayName("Given no enterprise edition license should throw an error")
    void givenNoEnterpriseEditionLicense_should_throwError() throws Exception {
      UpdateConnectorInstanceRequestedStatus input = new UpdateConnectorInstanceRequestedStatus();
      input.setRequestedStatus(ConnectorInstance.REQUESTED_STATUS_TYPE.starting);
      mvc.perform(
              put(CONNECTOR_INSTANCE_URI + "/fake-instance-id/requested-status")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.message").value("LICENSE_RESTRICTION"));
    }

    @Test
    @DisplayName("Given connector supported by manager should throw an error when xtmComposer down")
    void givenConnectorSupportedByManager_should_throwErrorIfXtmComposerDown() throws Exception {
      when(eeService.isLicenseActive(any())).thenReturn(true);

      CatalogConnector catalogConnector = getCatalogConnector();
      ConnectorInstance connectorInstance = getConnectorInstance(catalogConnector, Set.of());

      UpdateConnectorInstanceRequestedStatus input = new UpdateConnectorInstanceRequestedStatus();
      input.setRequestedStatus(ConnectorInstance.REQUESTED_STATUS_TYPE.starting);
      mvc.perform(
              put(CONNECTOR_INSTANCE_URI + "/" + connectorInstance.getId() + "/requested-status")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isBadRequest())
          .andExpect(
              result -> {
                String errorMessage = result.getResolvedException().getMessage();
                assertTrue(
                    errorMessage.contains(
                        "XTM Composer is not configured in the platform settings"));
              });

      Map<String, String> composerSettings = new HashMap<>();
      composerSettings.put(XTM_COMPOSER_ID.key(), "composer-id-test");
      composerSettings.put(XTM_COMPOSER_VERSION.key(), "composer-version-test");
      composerSettings.put(
          XTM_COMPOSER_LAST_CONNECTIVITY_CHECK.key(),
          Instant.now().minus(3, ChronoUnit.HOURS).toString());
      platformSettingsService.saveSettings(composerSettings);

      mvc.perform(
              put(CONNECTOR_INSTANCE_URI + "/" + connectorInstance.getId() + "/requested-status")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isBadRequest())
          .andExpect(
              result -> {
                String errorMessage = result.getResolvedException().getMessage();
                assertTrue(errorMessage.contains("XTM Composer is not reachable"));
              });
    }

    @Test
    @DisplayName("Should successfully update requested status")
    void shouldSuccessfullyUpdateRequestedStatus() throws Exception {
      when(eeService.isLicenseActive(any())).thenReturn(true);

      CatalogConnector catalogConnector = getCatalogConnector();
      ConnectorInstance connectorInstance = getConnectorInstance(catalogConnector, Set.of());

      UpdateConnectorInstanceRequestedStatus input = new UpdateConnectorInstanceRequestedStatus();
      input.setRequestedStatus(ConnectorInstance.REQUESTED_STATUS_TYPE.starting);

      Map<String, String> composerSettings = new HashMap<>();
      composerSettings.put(XTM_COMPOSER_ID.key(), "composer-id-test");
      composerSettings.put(XTM_COMPOSER_VERSION.key(), "composer-version-test");
      composerSettings.put(XTM_COMPOSER_LAST_CONNECTIVITY_CHECK.key(), Instant.now().toString());
      platformSettingsService.saveSettings(composerSettings);

      mvc.perform(
              put(CONNECTOR_INSTANCE_URI + "/" + connectorInstance.getId() + "/requested-status")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().is2xxSuccessful())
          .andReturn()
          .getResponse()
          .getContentAsString();

      Optional<ConnectorInstancePersisted> instanceSaved =
          connectorInstanceRepository.findById(connectorInstance.getId());
      assertTrue(instanceSaved.isPresent());
      assertTrue(
          ConnectorInstance.REQUESTED_STATUS_TYPE.starting.equals(
              instanceSaved.get().getRequestedStatus()));

      input.setRequestedStatus(ConnectorInstance.REQUESTED_STATUS_TYPE.stopping);
      mvc.perform(
              put(CONNECTOR_INSTANCE_URI + "/" + connectorInstance.getId() + "/requested-status")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().is2xxSuccessful())
          .andReturn()
          .getResponse()
          .getContentAsString();

      Optional<ConnectorInstancePersisted> instanceSaved2 =
          connectorInstanceRepository.findById(connectorInstance.getId());
      assertTrue(instanceSaved2.isPresent());
      assertTrue(
          ConnectorInstance.REQUESTED_STATUS_TYPE.stopping.equals(
              instanceSaved2.get().getRequestedStatus()));
    }
  }

  @Test
  @DisplayName("Given connector instance id should retrieve logs associated")
  void givenConnectorInstanceId_shouldRetrieveLogs() throws Exception {
    CatalogConnector catalogConnector = getCatalogConnector();
    ConnectorInstancePersisted connectorInstance1 =
        getConnectorInstance(catalogConnector, Set.of());
    ConnectorInstancePersisted connectorInstance2 =
        getConnectorInstance(catalogConnector, Set.of());
    ConnectorInstanceLog log0 = createConnectorInstanceLog("log 1");
    ConnectorInstanceLog log1 = createConnectorInstanceLog("log 2");
    ConnectorInstanceLog log2 = createConnectorInstanceLog("log 3");
    log0.setConnectorInstance(connectorInstance1);
    log1.setConnectorInstance(connectorInstance1);
    log2.setConnectorInstance(connectorInstance2);
    connectorInstanceLogRepository.saveAll(List.of(log0, log1, log2));

    String responseInstance1 =
        mvc.perform(
                get(CONNECTOR_INSTANCE_URI + "/" + connectorInstance1.getId() + "/logs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThatJson(responseInstance1).isArray().size().isEqualTo(2);
    assertThatJson(responseInstance1)
        .inPath("[*].connector_instance_log")
        .isArray()
        .containsExactlyInAnyOrderElementsOf(List.of("log 1", "log 2"));

    String responseInstance2 =
        mvc.perform(
                get(CONNECTOR_INSTANCE_URI + "/" + connectorInstance2.getId() + "/logs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThatJson(responseInstance2).isArray().size().isEqualTo(1);
    assertThatJson(responseInstance2)
        .inPath("[*].connector_instance_log")
        .isArray()
        .containsExactlyInAnyOrderElementsOf(List.of("log 3"));
  }
}
