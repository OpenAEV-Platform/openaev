package io.openaev.api.catalog.payload;

import static io.openaev.api.catalog.CatalogApi.TENANT_CATALOG_URI;
import static io.openaev.api.catalog.CatalogSettingsApi.CATALOG_SETTINGS_URI;
import static io.openaev.api.threat_arsenal.ThreatArsenalApi.TENANT_THREAT_ARSENAL_URL;
import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static io.openaev.rest.payload.PayloadApi.TENANT_PAYLOAD_URI;
import static io.openaev.service.catalog.payload.CatalogPayloadService.OTHER_TYPE_REFUSED;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.PAYLOAD_ID;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.manifest;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.payloadFileName;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.payloadVersion;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.releasedPayload;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.syncRequest;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.IntegrationTest;
import io.openaev.api.catalog.CatalogItemMapper;
import io.openaev.api.catalog.dto.CatalogSettingsInput;
import io.openaev.context.TxCtx;
import io.openaev.database.model.DetectionRemediation;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Payload;
import io.openaev.database.model.SecurityPlatform;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.catalog.CatalogHistory;
import io.openaev.database.repository.CatalogHistoryRepository;
import io.openaev.database.repository.DetectionRemediationRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.database.repository.SecurityPlatformRepository;
import io.openaev.exception.CatalogPayloadReadOnlyException;
import io.openaev.service.catalog.payload.CatalogPayloadInstaller;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.CatalogReleaseFixture.ReleasedPayload;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.PayloadFixture;
import io.openaev.utils.fixtures.SecurityPlatformFixture;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.Arrays;
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
@DisplayName("Catalogue payload install")
class CatalogPayloadInstallApiTest extends IntegrationTest {

  private static final ReleasedPayload RELEASED = releasedPayload();

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private PayloadRepository payloadRepository;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private CatalogHistoryRepository catalogHistoryRepository;
  @Autowired private InjectorFixture injectorFixture;
  @Autowired private CatalogPayloadInstaller catalogPayloadInstaller;
  @Autowired private SecurityPlatformRepository securityPlatformRepository;
  @Autowired private DetectionRemediationRepository detectionRemediationRepository;

  @BeforeEach
  void attachToDefaultTenant() {
    tenantHelper.attachCurrentUserToTenant(DEFAULT_TENANT_UUID);
    injectorFixture.getWellKnownOaevImplantInjector();
  }

  // -- HELPERS --

  /** One payload version a release lists. */
  private record Released(String version, String status, ReleasedPayload payload) {}

  private void publish(String release, String status, ReleasedPayload released, String... versions)
      throws Exception {
    publish(
        release,
        Arrays.stream(versions)
            .map(version -> new Released(version, status, released))
            .toArray(Released[]::new));
  }

  private void publish(String release, Released... versions) throws Exception {
    List<Map<String, Object>> payloads =
        Arrays.stream(versions)
            .map(v -> payloadVersion(v.version(), v.status(), v.payload()))
            .toList();
    Map<String, byte[]> files = new LinkedHashMap<>();
    Arrays.stream(versions)
        .forEach(v -> files.put(payloadFileName(v.version()), v.payload().file()));
    mvc.perform(
            syncRequest(
                    mapper.writeValueAsBytes(manifest(release, List.of(), payloads)),
                    files,
                    TENANT_CATALOG_URI + "/sync",
                    DEFAULT_TENANT_UUID)
                .with(csrf()))
        .andExpect(status().isOk());
  }

  private ResultActions install(String tenantId, String version) throws Exception {
    return committed(
        post(
            TENANT_CATALOG_URI + "/payloads/{externalId}/versions/{version}/install",
            tenantId,
            PAYLOAD_ID,
            version));
  }

  private ResultActions installAll() throws Exception {
    return committed(post(TENANT_CATALOG_URI + "/payloads/install-all", DEFAULT_TENANT_UUID));
  }

  private void autoInstall() {
    catalogPayloadInstaller.autoInstall(TxCtx.forTenant(DEFAULT_TENANT_UUID), DEFAULT_TENANT_UUID);
    entityManager.flush();
  }

  private ResultActions settings(boolean payloadAutoInstall) throws Exception {
    return committed(
        put(CATALOG_SETTINGS_URI, DEFAULT_TENANT_UUID)
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(new CatalogSettingsInput(payloadAutoInstall))));
  }

  private ResultActions uninstall(String tenantId, String payloadId) throws Exception {
    return committed(
        delete(TENANT_CATALOG_URI + "/installed-payloads/{payloadId}", tenantId, payloadId));
  }

  // The test transaction never commits: flush what a request changed, as its commit would.
  private ResultActions committed(MockHttpServletRequestBuilder request) throws Exception {
    ResultActions result = mvc.perform(request.with(csrf()));
    entityManager.flush();
    return result;
  }

  private List<String> installedIds(ResultActions result) throws Exception {
    JsonNode body = mapper.readTree(result.andReturn().getResponse().getContentAsString());
    return mapper.convertValue(
        body.path("installed_payload_ids"),
        mapper.getTypeFactory().constructCollectionType(List.class, String.class));
  }

  private String contractOf(String payloadId) {
    Payload payload = payloadRepository.findById(payloadId).orElseThrow();
    return injectorContractRepository
        .findInjectorContractByPayload(payload)
        .map(InjectorContract::getId)
        .orElseThrow();
  }

  private List<String> threatArsenalContracts() throws Exception {
    SearchPaginationInput input = new SearchPaginationInput();
    input.setTextSearch("Crowdoor");
    String response =
        mvc.perform(
                post(TENANT_THREAT_ARSENAL_URL + "/search", DEFAULT_TENANT_UUID)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(input)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return mapper.readTree(response).path("content").findValuesAsText("injector_contract_id");
  }

  private void rule(
      Payload payload,
      SecurityPlatform platform,
      String values,
      DetectionRemediation.AUTHOR_RULE author) {
    detectionRemediationRepository.save(
        DetectionRemediation.builder()
            .payload(payload)
            .securityPlatform(platform)
            .values(values)
            .authorRule(author)
            .build());
    entityManager.flush();
    // The payload loaded before does not list the rule: the next request reads it again.
    entityManager.clear();
  }

  private String localPayload() {
    Payload payload = PayloadFixture.createDefaultCommand();
    payload.setTenant(new Tenant(DEFAULT_TENANT_UUID));
    return payloadRepository.save(payload).getId();
  }

  private void runByInject(String payloadId) {
    injectRepository.save(
        InjectFixture.createInject(
            injectorContractRepository.findById(contractOf(payloadId)).orElseThrow(), "Inject"));
    entityManager.flush();
  }

  private List<String> autoInstalledVersions() {
    entityManager.flush();
    return catalogHistoryRepository.findAll().stream()
        .filter(row -> row.getMode() == CatalogHistory.Mode.AUTO)
        .map(CatalogHistory::getVersion)
        .toList();
  }

  private List<CatalogHistory.Action> history() {
    entityManager.flush();
    return catalogHistoryRepository.findAll().stream()
        .filter(row -> PAYLOAD_ID.equals(row.getExternalId()))
        .map(CatalogHistory::getAction)
        .toList();
  }

  // -- INSTALL --

  @Nested
  @DisplayName("Installing a version")
  class Installing {

    @Test
    @DisplayName("adds the released content as a Filigran action and records it")
    void given_releasedVersion_should_installItAndRecordIt() throws Exception {
      // Arrange
      publish("1.261109.0", "VERIFIED", RELEASED, "1.0.0");

      // Act
      List<String> ids =
          installedIds(install(DEFAULT_TENANT_UUID, "1.0.0").andExpect(status().isCreated()));

      // Assert
      Payload payload = payloadRepository.findById(ids.getFirst()).orElseThrow();
      assertThat(payload.isManaged()).isTrue();
      assertThat(payload.getVersion()).isEqualTo("1.0.0");
      assertThat(payload.getContentHash()).isEqualTo(RELEASED.contentHash());
      assertThat(payload.getStatus()).isEqualTo(Payload.PAYLOAD_STATUS.VERIFIED);
      assertThat(payload.getAuthorOrganization().getName()).isEqualTo(CatalogItemMapper.FILIGRAN);
      assertThat(threatArsenalContracts()).containsExactly(contractOf(payload.getId()));
      assertThat(history()).containsExactly(CatalogHistory.Action.INSTALL);
    }

    @Test
    @DisplayName("of an older version keeps only the highest in the Threat Arsenal")
    void given_olderVersionAfterNewer_should_listOnlyTheHighest() throws Exception {
      // Arrange
      publish("1.261109.0", "VERIFIED", RELEASED, "1.0.0", "1.1.0");
      List<String> highest =
          installedIds(install(DEFAULT_TENANT_UUID, "1.1.0").andExpect(status().isCreated()));

      // Act
      install(DEFAULT_TENANT_UUID, "1.0.0").andExpect(status().isCreated());

      // Assert
      assertThat(threatArsenalContracts()).containsExactly(contractOf(highest.getFirst()));
    }

    @Test
    @DisplayName("of an uninstalled payload brings the same version back")
    void given_uninstalledPayload_should_reinstallTheSameVersion() throws Exception {
      // Arrange
      publish("1.261109.0", "VERIFIED", RELEASED, "1.0.0");
      List<String> first =
          installedIds(install(DEFAULT_TENANT_UUID, "1.0.0").andExpect(status().isCreated()));
      uninstall(DEFAULT_TENANT_UUID, first.getFirst()).andExpect(status().isNoContent());

      // Act
      List<String> again =
          installedIds(install(DEFAULT_TENANT_UUID, "1.0.0").andExpect(status().isCreated()));

      // Assert
      assertThat(again).isEqualTo(first);
      assertThat(threatArsenalContracts()).containsExactly(contractOf(first.getFirst()));
      assertThat(history())
          .containsExactly(
              CatalogHistory.Action.INSTALL,
              CatalogHistory.Action.UNINSTALL,
              CatalogHistory.Action.INSTALL);
    }

    static Stream<Arguments> refusals() {
      return Stream.of(
          Arguments.of("an installed version", "VERIFIED", RELEASED, true, "1.0.0", 400),
          Arguments.of("a deprecated version", "DEPRECATED", RELEASED, false, "1.0.0", 400),
          Arguments.of(
              "a content without the release hash",
              "VERIFIED",
              new ReleasedPayload(RELEASED.file(), "b".repeat(64)),
              false,
              "1.0.0",
              400),
          Arguments.of("a version not released", "VERIFIED", RELEASED, false, "9.9.9", 404));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusals")
    @DisplayName("is refused for")
    void given_versionNotInstallable_should_refuseIt(
        String description,
        String status,
        ReleasedPayload released,
        boolean installedBefore,
        String version,
        int expected)
        throws Exception {
      // Arrange
      publish("1.261109.0", status, released, "1.0.0");
      if (installedBefore) {
        install(DEFAULT_TENANT_UUID, "1.0.0").andExpect(status().isCreated());
      }

      // Act & Assert
      install(DEFAULT_TENANT_UUID, version).andExpect(status().is(expected));
    }

    @Test
    @DisplayName("of a newer version carries the rules the tenant added, to review")
    void given_localRulesOnInstalledVersion_should_carryThemToReview() throws Exception {
      // Arrange
      publish("1.261109.0", "VERIFIED", RELEASED, "1.0.0");
      String installedId =
          installedIds(install(DEFAULT_TENANT_UUID, "1.0.0").andExpect(status().isCreated()))
              .getFirst();
      Payload installed = payloadRepository.findById(installedId).orElseThrow();
      SecurityPlatform platform =
          securityPlatformRepository.save(SecurityPlatformFixture.createDefault("SIEM", "SIEM"));
      rule(installed, platform, "ai query", DetectionRemediation.AUTHOR_RULE.AI);
      rule(installed, platform, "release query", DetectionRemediation.AUTHOR_RULE.HUMAN);
      publish("1.261110.0", "VERIFIED", RELEASED, "1.0.0", "1.1.0");

      // Act
      String newerId =
          installedIds(install(DEFAULT_TENANT_UUID, "1.1.0").andExpect(status().isCreated()))
              .getFirst();

      // Assert
      entityManager.clear();
      assertThat(payloadRepository.findById(newerId).orElseThrow().getDetectionRemediations())
          .extracting(DetectionRemediation::getValues, DetectionRemediation::getAuthorRule)
          .containsExactly(tuple("ai query", DetectionRemediation.AUTHOR_RULE.AI_OUTDATED));
    }

    @Test
    @DisplayName("of another type than the installed one is refused")
    void given_versionOfAnotherType_should_refuseIt() throws Exception {
      // Arrange
      ObjectNode otherType = (ObjectNode) mapper.readTree(RELEASED.file());
      otherType.put("payload_type", "DnsResolution");
      publish("1.261109.0", "VERIFIED", RELEASED, "1.0.0");
      install(DEFAULT_TENANT_UUID, "1.0.0").andExpect(status().isCreated());
      publish(
          "1.261110.0",
          new Released("1.0.0", "VERIFIED", RELEASED),
          new Released(
              "1.1.0",
              "VERIFIED",
              new ReleasedPayload(mapper.writeValueAsBytes(otherType), RELEASED.contentHash())));

      // Act & Assert
      install(DEFAULT_TENANT_UUID, "1.1.0")
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value(OTHER_TYPE_REFUSED));
    }

    @Test
    @DisplayName("of every payload never installed skips an uninstalled one")
    void given_installAll_should_installNeverInstalledOnly() throws Exception {
      // Arrange
      publish("1.261109.0", "VERIFIED", RELEASED, "1.0.0");

      // Act
      List<String> first = installedIds(installAll().andExpect(status().isCreated()));
      uninstall(DEFAULT_TENANT_UUID, first.getFirst()).andExpect(status().isNoContent());
      List<String> again = installedIds(installAll().andExpect(status().isCreated()));

      // Assert
      assertThat(first).hasSize(1);
      assertThat(again).isEmpty();
    }
  }

  // -- AUTO-INSTALL --

  @Nested
  @DisplayName("Installing by itself after a release")
  class AutoInstalling {

    @Test
    @DisplayName("brings every newer offered version of an installed payload, whatever the setting")
    void given_installedPayload_should_installItsNewerOfferedVersions() throws Exception {
      // Arrange
      publish("1.261109.0", "VERIFIED", RELEASED, "1.0.0");
      install(DEFAULT_TENANT_UUID, "1.0.0").andExpect(status().isCreated());
      settings(false).andExpect(status().isOk());
      publish(
          "1.261110.0",
          new Released("0.9.0", "VERIFIED", RELEASED),
          new Released("1.0.0", "VERIFIED", RELEASED),
          new Released("1.2.0", "VERIFIED", RELEASED),
          new Released("1.1.0", "VERIFIED", RELEASED),
          new Released("1.3.0", "DEPRECATED", RELEASED),
          new Released("1.4.0", "REVOKED", RELEASED));

      // Act
      autoInstall();

      // Assert
      assertThat(autoInstalledVersions()).containsExactly("1.1.0", "1.2.0");
      assertThat(threatArsenalContracts()).hasSize(1);
    }

    static Stream<Arguments> payloadsNotInstalled() {
      return Stream.of(
          Arguments.of("never installed, setting on", true, false, List.of("1.1.0")),
          Arguments.of("never installed, setting off", false, false, List.of()),
          Arguments.of("uninstalled, setting on", true, true, List.of()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("payloadsNotInstalled")
    @DisplayName("of a payload not installed installs its latest version only when asked")
    void given_payloadNotInstalled_should_followTheSetting(
        String description, boolean autoInstallOn, boolean uninstalled, List<String> expected)
        throws Exception {
      // Arrange
      publish("1.261109.0", "VERIFIED", RELEASED, "1.0.0");
      if (uninstalled) {
        String payloadId =
            installedIds(install(DEFAULT_TENANT_UUID, "1.0.0").andExpect(status().isCreated()))
                .getFirst();
        uninstall(DEFAULT_TENANT_UUID, payloadId).andExpect(status().isNoContent());
      }
      settings(autoInstallOn).andExpect(status().isOk());
      publish("1.261110.0", "VERIFIED", RELEASED, "1.0.0", "1.1.0");

      // Act
      autoInstall();

      // Assert
      assertThat(autoInstalledVersions()).isEqualTo(expected);
    }
  }

  // -- SETTINGS --

  @Nested
  @DisplayName("The catalogue settings")
  class Settings {

    private String readSettings() throws Exception {
      return mvc.perform(get(CATALOG_SETTINGS_URI, DEFAULT_TENANT_UUID))
          .andExpect(status().isOk())
          .andReturn()
          .getResponse()
          .getContentAsString();
    }

    @Test
    @DisplayName("install new payloads by default, until an admin turns it off")
    void given_settingTurnedOff_should_readItBackOff() throws Exception {
      // Act
      String before = readSettings();
      settings(false).andExpect(status().isOk());
      String after = readSettings();

      // Assert
      assertThatJson(before).node("payload_auto_install").isEqualTo(true);
      assertThatJson(after).node("payload_auto_install").isEqualTo(false);
    }
  }

  // -- UNINSTALL --

  @Nested
  @DisplayName("Uninstalling a payload")
  class Uninstalling {

    @Test
    @DisplayName("hides it from the Threat Arsenal and records it")
    void given_installedPayload_should_hideItAndRecordIt() throws Exception {
      // Arrange
      publish("1.261109.0", "VERIFIED", RELEASED, "1.0.0");
      String payloadId =
          installedIds(install(DEFAULT_TENANT_UUID, "1.0.0").andExpect(status().isCreated()))
              .getFirst();

      // Act
      uninstall(DEFAULT_TENANT_UUID, payloadId).andExpect(status().isNoContent());

      // Assert
      assertThat(threatArsenalContracts()).isEmpty();
      assertThat(payloadRepository.findById(payloadId)).isPresent();
      assertThat(history())
          .containsExactly(CatalogHistory.Action.INSTALL, CatalogHistory.Action.UNINSTALL);
    }

    /** The payload a refused uninstall points at, once its case is arranged. */
    @FunctionalInterface
    interface Arrange {
      String payloadId(CatalogPayloadInstallApiTest test, String installedId) throws Exception;
    }

    static Stream<Arguments> refusals() {
      return Stream.of(
          Arguments.of(
              "an inject runs it",
              (Arrange)
                  (test, id) -> {
                    test.runByInject(id);
                    return id;
                  }),
          Arguments.of(
              "it is already uninstalled",
              (Arrange)
                  (test, id) -> {
                    test.uninstall(DEFAULT_TENANT_UUID, id).andExpect(status().isNoContent());
                    return id;
                  }),
          Arguments.of("it is a local payload", (Arrange) (test, id) -> test.localPayload()));
    }

    @Test
    @DisplayName("from another tenant does not find it")
    void given_otherTenant_should_notFindThePayload() throws Exception {
      // Arrange
      String other = tenantHelper.createTenantWithCurrentUser("catalogue-other").getId();
      publish("1.261109.0", "VERIFIED", RELEASED, "1.0.0");
      String payloadId =
          installedIds(install(DEFAULT_TENANT_UUID, "1.0.0").andExpect(status().isCreated()))
              .getFirst();

      // Act & Assert
      uninstall(other, payloadId).andExpect(status().isNotFound());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusals")
    @DisplayName("is refused when")
    void given_payloadNotUninstallable_should_refuseIt(String description, Arrange arrange)
        throws Exception {
      // Arrange
      publish("1.261109.0", "VERIFIED", RELEASED, "1.0.0");
      String installedId =
          installedIds(install(DEFAULT_TENANT_UUID, "1.0.0").andExpect(status().isCreated()))
              .getFirst();
      String payloadId = arrange.payloadId(CatalogPayloadInstallApiTest.this, installedId);

      // Act & Assert
      uninstall(DEFAULT_TENANT_UUID, payloadId).andExpect(status().isBadRequest());
    }
  }

  // -- GUARDS --

  @Nested
  @DisplayName("An installed version")
  class ReadOnly {

    @Test
    @DisplayName("is neither deleted nor changed")
    void given_installedVersion_should_refuseDeleteAndChange() throws Exception {
      // Arrange
      publish("1.261109.0", "VERIFIED", RELEASED, "1.0.0");
      String payloadId =
          installedIds(install(DEFAULT_TENANT_UUID, "1.0.0").andExpect(status().isCreated()))
              .getFirst();

      // Act & Assert
      mvc.perform(delete(TENANT_PAYLOAD_URI + "/{id}", DEFAULT_TENANT_UUID, payloadId).with(csrf()))
          .andExpect(status().isBadRequest());
      payloadRepository.findById(payloadId).orElseThrow().setName("Changed");
      assertThatThrownBy(() -> entityManager.flush())
          .isInstanceOf(CatalogPayloadReadOnlyException.class);
    }
  }
}
