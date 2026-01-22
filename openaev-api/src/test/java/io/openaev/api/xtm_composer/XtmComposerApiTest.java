package io.openaev.api.xtm_composer;

import static io.openaev.api.xtm_composer.XtmComposerApi.XTMCOMPOSER_URI;
import static io.openaev.database.model.SettingKeys.*;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.CatalogConnectorFixture.createDefaultCatalogConnectorManagedByXtmComposer;
import static io.openaev.utils.fixtures.ConnectorInstanceFixture.createDefaultConnectorInstance;
import static io.openaev.utils.fixtures.ConnectorInstanceFixture.createDefaultConnectorInstanceConfiguration;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.api.xtm_composer.dto.XtmComposerRegisterInput;
import io.openaev.api.xtm_composer.dto.XtmComposerUpdateStatusInput;
import io.openaev.config.OpenAEVConfig;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorInstanceLog;
import io.openaev.database.model.ConnectorInstancePersisted;
import io.openaev.database.model.Setting;
import io.openaev.database.repository.ConnectorInstanceLogRepository;
import io.openaev.database.repository.ConnectorInstanceRepository;
import io.openaev.rest.connector_instance.dto.ConnectorInstanceHealthInput;
import io.openaev.rest.connector_instance.dto.ConnectorInstanceLogsInput;
import io.openaev.service.PlatformSettingsService;
import io.openaev.utils.fixtures.composers.CatalogConnectorComposer;
import io.openaev.utils.fixtures.composers.ConnectorInstanceComposer;
import io.openaev.utils.fixtures.composers.ConnectorInstanceConfigurationComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@TestInstance(PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("XTM Composer API Integration Tests")
public class XtmComposerApiTest extends IntegrationTest {

  @Autowired private OpenAEVConfig openAEVConfig;
  @Autowired private MockMvc mvc;
  @Autowired private PlatformSettingsService platformSettingsService;
  @Autowired private ConnectorInstanceRepository connectorInstanceRepository;
  @Autowired private ConnectorInstanceLogRepository connectorInstanceLogRepository;

  @Autowired private CatalogConnectorComposer catalogConnectorComposer;
  @Autowired private ConnectorInstanceComposer connectorInstanceComposer;
  @Autowired private ConnectorInstanceConfigurationComposer connectorInstanceConfigurationComposer;

  @BeforeAll
  void setUp() {
    openAEVConfig.setVersion("test-version");
  }

  @Nested
  @DisplayName("Manage xtmComposer registration endpoints")
  class XtmComposerManager {
    @Nested
    class Registration {
      @Test
      @DisplayName("When XtmComposer up should register into API")
      void givenXtmComposerUp_should_register() throws Exception {
        XtmComposerRegisterInput input = new XtmComposerRegisterInput();
        String name = "Xtm composer test";
        String id = "test-xtm-composer";
        String publicKey = "public-key-test";
        input.setName(name);
        input.setId(id);
        input.setPublicKey(publicKey);

        String response =
            mvc.perform(
                    post(XTMCOMPOSER_URI + "/register")
                        .content(asJsonString(input))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String responseToken = JsonPath.read(response, "$.xtm_composer_id");
        String responseVersion = JsonPath.read(response, "$.xtm_composer_version");

        String openAEVversion = platformSettingsService.getPlatformVersion();
        Map<String, Setting> settingsMap =
            platformSettingsService.findSettingsByKeys(
                List.of(
                    XTM_COMPOSER_ID.key(),
                    XTM_COMPOSER_NAME.key(),
                    XTM_COMPOSER_VERSION.key(),
                    XTM_COMPOSER_PUBLIC_KEY.key(),
                    XTM_COMPOSER_LAST_CONNECTIVITY_CHECK.key()));
        assertEquals(responseToken, id);
        assertEquals(responseVersion, openAEVversion);
        assertEquals(settingsMap.get(XTM_COMPOSER_ID.key()).getValue(), id);
        assertEquals(settingsMap.get(XTM_COMPOSER_NAME.key()).getValue(), name);
        assertEquals(settingsMap.get(XTM_COMPOSER_VERSION.key()).getValue(), openAEVversion);
        assertEquals(settingsMap.get(XTM_COMPOSER_PUBLIC_KEY.key()).getValue(), publicKey);
        assertNotNull(settingsMap.get(XTM_COMPOSER_LAST_CONNECTIVITY_CHECK.key()).getValue());
      }

      @Test
      @DisplayName("Should update XtmComposer registration information")
      void givenXtmComposerAlreadyRegistered_should_updateRegistrationInfo() throws Exception {
        XtmComposerRegisterInput input = new XtmComposerRegisterInput();
        input.setName("Xtm composer  old");
        input.setId("test-xtm-composer-old");
        input.setPublicKey("public-key-test-old");
        mvc.perform(
                post(XTMCOMPOSER_URI + "/register")
                    .content(asJsonString(input))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

        String name = "Xtm composer test old";
        String id = "test-xtm-composer-old";
        String publicKey = "public-key-test-old";
        input.setName(name);
        input.setId(id);
        input.setPublicKey(publicKey);
        String response =
            mvc.perform(
                    post(XTMCOMPOSER_URI + "/register")
                        .content(asJsonString(input))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String responseToken = JsonPath.read(response, "$.xtm_composer_id");
        Map<String, Setting> settingsMap =
            platformSettingsService.findSettingsByKeys(
                List.of(
                    XTM_COMPOSER_ID.key(),
                    XTM_COMPOSER_NAME.key(),
                    XTM_COMPOSER_VERSION.key(),
                    XTM_COMPOSER_PUBLIC_KEY.key(),
                    XTM_COMPOSER_LAST_CONNECTIVITY_CHECK.key()));
        assertEquals(responseToken, id);
        assertEquals(settingsMap.get(XTM_COMPOSER_ID.key()).getValue(), id);
        assertEquals(settingsMap.get(XTM_COMPOSER_NAME.key()).getValue(), name);
        assertEquals(settingsMap.get(XTM_COMPOSER_PUBLIC_KEY.key()).getValue(), publicKey);
        assertNotNull(settingsMap.get(XTM_COMPOSER_LAST_CONNECTIVITY_CHECK.key()).getValue());
      }
    }

    @Nested
    class RefreshConnectivity {
      @Test
      @DisplayName("Should refresh connectivity")
      void should_refreshConnectivity() throws Exception {
        Map<String, String> composerSettings = new HashMap<>();
        composerSettings.put(XTM_COMPOSER_ID.key(), "composer-id-test");
        composerSettings.put(XTM_COMPOSER_VERSION.key(), "composer-version-test");
        platformSettingsService.saveSettings(composerSettings);
        Instant timeBeforeRequest = Instant.now();

        mvc.perform(
                put(XTMCOMPOSER_URI + "/composer-id-test/refresh-connectivity")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

        Optional<Setting> lastConnectivitySetting =
            platformSettingsService.setting(XTM_COMPOSER_LAST_CONNECTIVITY_CHECK.key());
        assertTrue(lastConnectivitySetting.isPresent());
        assertTrue(
            timeBeforeRequest.isBefore(Instant.parse(lastConnectivitySetting.get().getValue())));
      }

      @Test
      @DisplayName("")
      void givenFakeXtmComposerId_should_throwErrorWhenRefreshConnectivity() throws Exception {
        Map<String, String> composerSettings = new HashMap<>();
        composerSettings.put(XTM_COMPOSER_ID.key(), "composer-id-test");
        composerSettings.put(XTM_COMPOSER_VERSION.key(), "composer-version-test");
        platformSettingsService.saveSettings(composerSettings);
        mvc.perform(
                put(XTMCOMPOSER_URI + "/fake-composer-id/refresh-connectivity")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest());
      }
    }

    @Nested
    class Reachable {
      @Test
      @DisplayName("Returns false when connectivity check is over 2 hours old")
      void givenExpiredConnectivity_should_returnFalse() throws Exception {
        Map<String, String> composerSettings = new HashMap<>();
        composerSettings.put(XTM_COMPOSER_ID.key(), "composer-id-test");
        composerSettings.put(XTM_COMPOSER_VERSION.key(), "composer-version-test");
        composerSettings.put(
            XTM_COMPOSER_LAST_CONNECTIVITY_CHECK.key(),
            Instant.now().minus(3, ChronoUnit.HOURS).toString());
        platformSettingsService.saveSettings(composerSettings);
        String response =
            mvc.perform(
                    get(XTMCOMPOSER_URI + "/reachable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertTrue(response.contains("false"));
      }

      @Test
      @DisplayName("Returns true when XTM Composer is reachable")
      void givenActiveConnection_should_returnTrue() throws Exception {
        Instant now = Instant.now();
        Map<String, String> composerSettings = new HashMap<>();
        composerSettings.put(XTM_COMPOSER_ID.key(), "composer-id-test");
        composerSettings.put(XTM_COMPOSER_VERSION.key(), "composer-version-test");
        composerSettings.put(XTM_COMPOSER_LAST_CONNECTIVITY_CHECK.key(), now.toString());
        platformSettingsService.saveSettings(composerSettings);
        String response =
            mvc.perform(
                    get(XTMCOMPOSER_URI + "/reachable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertTrue(response.contains("true"));
      }
    }
  }

  @Nested
  @DisplayName("Instances management from xtmComposer endpoints")
  class XtmComposerInstance {
    private ConnectorInstancePersisted getConnectorInstance(String connectorName)
        throws JsonProcessingException {
      return connectorInstanceComposer
          .forConnectorInstance(createDefaultConnectorInstance())
          .withConnectorInstanceConfiguration(
              connectorInstanceConfigurationComposer.forConnectorInstanceConfiguration(
                  createDefaultConnectorInstanceConfiguration()))
          .withCatalogConnector(
              catalogConnectorComposer.forCatalogConnector(
                  createDefaultCatalogConnectorManagedByXtmComposer(connectorName)))
          .persist()
          .get();
    }

    @Nested
    class RetrieveInstances {
      @Test
      @DisplayName("Given fake composer id should throw error")
      void givenFakeComposerId_should_throwError() throws Exception {
        Map<String, String> composerSettings = new HashMap<>();
        composerSettings.put(XTM_COMPOSER_ID.key(), "composer-id-test");
        composerSettings.put(XTM_COMPOSER_VERSION.key(), "composer-version-test");
        platformSettingsService.saveSettings(composerSettings);
        mvc.perform(
                get(XTMCOMPOSER_URI + "/fake-composer-id/connector-instances")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(
                result -> {
                  String errorMessage = result.getResolvedException().getMessage();
                  assertTrue(errorMessage.contains("Invalid xtm-composer identifier"));
                });
      }

      @Test
      @DisplayName(
          "Should retrieve all connector instances with configurations managed by xtmComposer")
      void should_retrieveAllInstancesManagedByXtmComposer() throws Exception {
        ConnectorInstancePersisted instance = getConnectorInstance("Microsoft defender collector");
        ConnectorInstancePersisted instance2 = getConnectorInstance("Microsoft sentinel collector");

        Map<String, String> composerSettings = new HashMap<>();
        composerSettings.put(XTM_COMPOSER_ID.key(), "composer-id-test");
        composerSettings.put(XTM_COMPOSER_VERSION.key(), "composer-version-test");
        platformSettingsService.saveSettings(composerSettings);
        String response =
            mvc.perform(
                    get(XTMCOMPOSER_URI + "/composer-id-test/connector-instances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThatJson(response).isArray().size().isEqualTo(2);
        assertThatJson(response)
            .inPath("[*].connector_instance_id")
            .isArray()
            .contains(instance.getId(), instance2.getId());
        assertThatJson(response)
            .inPath("[*].connector_instance_hash")
            .isArray()
            .size()
            .isEqualTo(2); // Check is hash exists on output value
        assertThatJson(response)
            .inPath("[*].connector_instance_name")
            .isArray()
            .contains(
                instance.getCatalogConnector().getTitle(),
                instance2.getCatalogConnector().getTitle());
        assertThatJson(response)
            .inPath("[*].connector_image")
            .isArray()
            .contains(
                String.format(
                    "%s:%s",
                    instance.getCatalogConnector().getContainerImage(),
                    instance.getCatalogConnector().getContainerVersion()),
                String.format(
                    "%s:%s",
                    instance2.getCatalogConnector().getContainerImage(),
                    instance2.getCatalogConnector().getContainerVersion()));
        assertThatJson(response)
            .inPath("[0].connector_instance_configurations")
            .isArray()
            .size()
            .isEqualTo(1);
      }
    }

    @Nested
    class UpdateStatus {
      @Test
      @DisplayName("Given fake composer id should throw error")
      void givenFakeComposerId_should_throwError() throws Exception {
        Map<String, String> composerSettings = new HashMap<>();
        composerSettings.put(XTM_COMPOSER_ID.key(), "composer-id-test");
        composerSettings.put(XTM_COMPOSER_VERSION.key(), "composer-version-test");
        platformSettingsService.saveSettings(composerSettings);
        XtmComposerUpdateStatusInput input = new XtmComposerUpdateStatusInput();
        input.setCurrentStatus(ConnectorInstance.CURRENT_STATUS_TYPE.started);
        mvc.perform(
                put(XTMCOMPOSER_URI
                        + "/fake-composer-id/connector-instances/fake-instance-id/status")
                    .content(asJsonString(input))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(
                result -> {
                  String errorMessage = result.getResolvedException().getMessage();
                  assertTrue(errorMessage.contains("Invalid xtm-composer identifier"));
                });
      }

      @Test
      @DisplayName("Given current status should update instance")
      void givenCurrentStatus_should_updateInstance() throws Exception {
        ConnectorInstance instance = getConnectorInstance("Microsoft defender collector");
        Map<String, String> composerSettings = new HashMap<>();
        composerSettings.put(XTM_COMPOSER_ID.key(), "composer-id-test");
        composerSettings.put(XTM_COMPOSER_VERSION.key(), "composer-version-test");
        platformSettingsService.saveSettings(composerSettings);

        XtmComposerUpdateStatusInput input = new XtmComposerUpdateStatusInput();
        input.setCurrentStatus(ConnectorInstance.CURRENT_STATUS_TYPE.started);
        mvc.perform(
                put(XTMCOMPOSER_URI
                        + "/composer-id-test/connector-instances/"
                        + instance.getId()
                        + "/status")
                    .content(asJsonString(input))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

        Optional<ConnectorInstancePersisted> instanceDb =
            connectorInstanceRepository.findById(instance.getId());
        assertTrue(instanceDb.isPresent());
        assertEquals(
            instanceDb.get().getCurrentStatus(), ConnectorInstance.CURRENT_STATUS_TYPE.started);

        XtmComposerUpdateStatusInput input2 = new XtmComposerUpdateStatusInput();
        input2.setCurrentStatus(ConnectorInstance.CURRENT_STATUS_TYPE.stopped);
        mvc.perform(
                put(XTMCOMPOSER_URI
                        + "/composer-id-test/connector-instances/"
                        + instance.getId()
                        + "/status")
                    .content(asJsonString(input2))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

        Optional<ConnectorInstancePersisted> instanceDb2 =
            connectorInstanceRepository.findById(instance.getId());
        assertTrue(instanceDb2.isPresent());
        assertEquals(
            instanceDb2.get().getCurrentStatus(), ConnectorInstance.CURRENT_STATUS_TYPE.stopped);
      }
    }

    @Nested
    class Logs {
      @Test
      @DisplayName("Given fake composer id should throw error")
      void givenFakeComposerId_should_throwError() throws Exception {
        Map<String, String> composerSettings = new HashMap<>();
        composerSettings.put(XTM_COMPOSER_ID.key(), "composer-id-test");
        composerSettings.put(XTM_COMPOSER_VERSION.key(), "composer-version-test");
        platformSettingsService.saveSettings(composerSettings);
        ConnectorInstanceLogsInput input = new ConnectorInstanceLogsInput();
        input.setLogs(new HashSet<>());
        mvc.perform(
                post(XTMCOMPOSER_URI
                        + "/fake-composer-id/connector-instances/fake-instance-id/logs")
                    .content(asJsonString(input))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(
                result -> {
                  String errorMessage = result.getResolvedException().getMessage();
                  assertTrue(errorMessage.contains("Invalid xtm-composer identifier"));
                });
      }

      @Test
      @DisplayName("Should add logs for a specific instance")
      void givenLogs_should_pushLogsForInstance() throws Exception {
        ConnectorInstance instance = getConnectorInstance("Microsoft defender collector");

        Map<String, String> composerSettings = new HashMap<>();
        composerSettings.put(XTM_COMPOSER_ID.key(), "composer-id-test");
        composerSettings.put(XTM_COMPOSER_VERSION.key(), "composer-version-test");
        platformSettingsService.saveSettings(composerSettings);

        ConnectorInstanceLogsInput input = new ConnectorInstanceLogsInput();
        Set<String> logs = new HashSet<>();
        logs.add("New logs");
        logs.add("New logs 2");
        input.setLogs(logs);
        List<ConnectorInstanceLog> instanceLogsDb =
            connectorInstanceLogRepository.findByConnectorInstanceId(instance.getId());
        assertEquals(0, instanceLogsDb.size());

        mvc.perform(
                post(XTMCOMPOSER_URI
                        + "/composer-id-test/connector-instances/"
                        + instance.getId()
                        + "/logs")
                    .content(asJsonString(input))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

        List<ConnectorInstanceLog> instanceLogsDbAfter =
            connectorInstanceLogRepository.findByConnectorInstanceId(instance.getId());
        assertEquals(1, instanceLogsDbAfter.size());
      }
    }

    @Nested
    class HealthCheck {
      @Test
      @DisplayName("Given fake composer id should throw error")
      void givenFakeComposerId_should_throwError() throws Exception {
        Map<String, String> composerSettings = new HashMap<>();
        composerSettings.put(XTM_COMPOSER_ID.key(), "composer-id-test");
        composerSettings.put(XTM_COMPOSER_VERSION.key(), "composer-version-test");
        platformSettingsService.saveSettings(composerSettings);
        ConnectorInstanceHealthInput input = new ConnectorInstanceHealthInput();
        input.setStartedAt(Instant.now());
        input.setRestartCount(1);
        input.setInRebootLoop(false);
        mvc.perform(
                put(XTMCOMPOSER_URI
                        + "/fake-composer-id/connector-instances/fake-instance-id/health-check")
                    .content(asJsonString(input))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(
                result -> {
                  String errorMessage = result.getResolvedException().getMessage();
                  assertTrue(errorMessage.contains("Invalid xtm-composer identifier"));
                });
      }

      @Test
      @DisplayName("Given healt-check informations should update instance")
      void givenHealthCheck_should_updateInstance() throws Exception {
        ConnectorInstance instance = getConnectorInstance("Microsoft defender collector");

        Map<String, String> composerSettings = new HashMap<>();
        composerSettings.put(XTM_COMPOSER_ID.key(), "composer-id-test");
        composerSettings.put(XTM_COMPOSER_VERSION.key(), "composer-version-test");
        platformSettingsService.saveSettings(composerSettings);
        ConnectorInstanceHealthInput input = new ConnectorInstanceHealthInput();
        Instant startTime = Instant.now();
        input.setStartedAt(startTime);
        input.setRestartCount(16);
        input.setInRebootLoop(false);

        mvc.perform(
                put(XTMCOMPOSER_URI
                        + "/composer-id-test/connector-instances/"
                        + instance.getId()
                        + "/health-check")
                    .content(asJsonString(input))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

        Optional<ConnectorInstancePersisted> instanceDb =
            connectorInstanceRepository.findById(instance.getId());
        assertTrue(instanceDb.isPresent());
        assertEquals(instanceDb.get().getRestartCount(), 16);
        assertEquals(instanceDb.get().getStartedAt(), startTime);
        assertFalse(instanceDb.get().isInRebootLoop());
      }
    }
  }
}
