package io.openaev.processor.core;

import static io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE.DETECTION;
import static io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE.LOGGING;
import static io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE.PREVENTION;
import static io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE.VULNERABILITY;
import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.IntegrationTest;
import io.openaev.api.expectations.ExpectationsDriftService;
import io.openaev.config.OpenAEVConfig;
import io.openaev.context.TenantContext;
import io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Payload;
import io.openaev.database.model.SecurityPlatform.SECURITY_PLATFORM_TYPE;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.processor.MigrationProcessingResult;
import io.openaev.rest.payload.service.PayloadService;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.DataPackService;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.PayloadFixture;
import io.openaev.utils.fixtures.composers.PayloadComposer;
import io.openaev.utils.injector_contract.InjectorContractContentUtils;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Add Logging expectation to manual payloads migration tests")
@Transactional
class V20261119_Add_logging_expectation_to_manual_payloadsTest extends IntegrationTest {

  @Autowired private V20261119_Add_logging_expectation_to_manual_payloads migration;
  @Autowired private PayloadComposer payloadComposer;
  @Autowired private PayloadRepository payloadRepository;
  @Autowired private PayloadService payloadService;
  @Autowired private InjectorRepository injectorRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private InjectorContractContentUtils injectorContractContentUtils;
  @Autowired private ExpectationsDriftService expectationsDriftService;
  @Autowired private DataPackService dataPackService;
  @Autowired private OpenAEVConfig openAEVConfig;
  @Autowired private CacheManager cacheManager;
  @Autowired private ObjectMapper mapper;
  @Autowired private EntityManager entityManager;

  private String originalDevFeatures;

  @BeforeEach
  void saveDevFeatures() {
    originalDevFeatures = openAEVConfig.getEnabledDevFeatures();
    injectorRepository.save(InjectorFixture.createDefaultPayloadInjector());
  }

  @AfterEach
  void restoreDevFeatures() {
    // The config bean is shared by the Spring context: restore the flags of the test profile
    setLoggingFeature(originalDevFeatures);
  }

  private void setLoggingFeature(String devFeatures) {
    openAEVConfig.setEnabledDevFeatures(devFeatures);
    Optional.ofNullable(cacheManager.getCache("global")).ifPresent(Cache::clear);
  }

  private Tenant tenant() {
    return new Tenant(TenantContext.getCurrentTenant());
  }

  /**
   * A payload created before Logging existed: its contract is built while the preview feature is
   * off, so it offers no Logging expectation.
   */
  private InjectorContract persistPayloadWithContract(
      Payload.PAYLOAD_SOURCE source,
      EXPECTATION_TYPE[] expectations,
      Map<EXPECTATION_TYPE, List<SECURITY_PLATFORM_TYPE>> platforms) {
    setLoggingFeature("");
    Payload payload = PayloadFixture.createDefaultCommand();
    payload.setSource(source);
    payload.setExpectations(expectations);
    payload.setExpectedSecurityPlatforms(new HashMap<>(platforms));
    payloadComposer.forPayload(payload).persist();
    InjectorContract contract =
        payloadService.synchroniseInjectorContractBasedOnPayload(
            payload, List.of(), new HashSet<>(), new HashSet<>());
    assertNotNull(contract, "The payload injector must build the contract");
    setLoggingFeature(PreviewFeature.LOGGING_EXPECTATION.getValue());
    return contract;
  }

  /**
   * An inject storing a copy of its contract's predefined expectations, as the inject form does.
   */
  private Inject persistInjectStoringContractExpectations(InjectorContract contract) {
    ArrayNode expectations = mapper.createArrayNode();
    injectorContractContentUtils
        .getPredefinedExpectationNodes(contract)
        .forEach(node -> expectations.add(node.deepCopy()));
    return persistInject(contract, expectations);
  }

  private Inject persistInject(InjectorContract contract, ArrayNode expectations) {
    Inject inject = InjectFixture.createInject(contract, "Inject");
    ObjectNode content = mapper.createObjectNode();
    if (expectations != null) {
      content.set(CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS, expectations);
    }
    inject.setContent(content);
    return injectRepository.save(inject);
  }

  // The migration writes the inject content with a bulk update: read the rows back from the
  // database, not from the session.
  private void clearSession() {
    entityManager.flush();
    entityManager.clear();
  }

  private Payload reloadPayload(InjectorContract contract) {
    clearSession();
    return payloadRepository.findById(contract.getPayload().getId()).orElseThrow();
  }

  private Inject reloadInject(Inject inject) {
    clearSession();
    return injectRepository.findById(inject.getId()).orElseThrow();
  }

  private static Set<String> types(JsonNode expectations) {
    Set<String> types = new HashSet<>();
    if (expectations != null) {
      expectations.forEach(node -> types.add(node.path("expectation_type").asText()));
    }
    return types;
  }

  private static Optional<JsonNode> expectation(JsonNode expectations, EXPECTATION_TYPE type) {
    return StreamSupport.stream(expectations.spliterator(), false)
        .filter(node -> type.name().equals(node.path("expectation_type").asText()))
        .findFirst();
  }

  private static List<String> platforms(JsonNode expectation) {
    List<String> platforms = new ArrayList<>();
    expectation
        .path("expectation_expected_security_platform_types")
        .forEach(node -> platforms.add(node.asText()));
    return platforms;
  }

  @Nested
  @DisplayName("When the migration cannot run yet")
  class Deferred {

    private InjectorContract persistManualPayloadExpectingDetection() {
      return persistPayloadWithContract(
          Payload.PAYLOAD_SOURCE.MANUAL, new EXPECTATION_TYPE[] {DETECTION}, Map.of());
    }

    private void assertChangedNothingAndNotRecorded(InjectorContract contract) {
      assertFalse(
          List.of(reloadPayload(contract).getExpectations()).contains(LOGGING),
          "The payload must not get Logging");
      assertTrue(
          dataPackService.findByIdAndTenant(migration.getMigrationId(), tenant()).isEmpty(),
          "The migration must not be recorded, to run at the next startup");
    }

    @Test
    @DisplayName("Should change nothing while the Logging preview feature is off")
    void given_featureOff_should_changeNothingAndNotRecordTheMigration() {
      // Arrange
      InjectorContract contract = persistManualPayloadExpectingDetection();
      setLoggingFeature("");

      // Act
      MigrationProcessingResult result = migration.process(tenant());

      // Assert
      assertEquals(MigrationProcessingResult.PROCESSED, result);
      assertChangedNothingAndNotRecorded(contract);
    }

    @Test
    @DisplayName("Should change nothing while no injector can rebuild the contracts")
    void given_noPayloadInjector_should_changeNothingAndNotRecordTheMigration() {
      // Arrange
      InjectorContract contract = persistManualPayloadExpectingDetection();
      injectorRepository
          .findAllByPayloads(true)
          .forEach(
              injector -> {
                injector.setPayloads(false);
                injectorRepository.save(injector);
              });

      // Act
      MigrationProcessingResult result = migration.process(tenant());

      // Assert
      assertEquals(MigrationProcessingResult.PROCESSED, result);
      assertChangedNothingAndNotRecorded(contract);
    }
  }

  @Nested
  @DisplayName("With the Logging preview feature on")
  class FeatureOn {

    @Test
    @DisplayName("Should add Logging to a manual payload, with its Detection platforms")
    void given_manualPayloadExpectingDetection_should_addLoggingToPayloadAndContract() {
      // Arrange
      InjectorContract contract =
          persistPayloadWithContract(
              Payload.PAYLOAD_SOURCE.MANUAL,
              new EXPECTATION_TYPE[] {PREVENTION, DETECTION},
              Map.of(DETECTION, List.of(SECURITY_PLATFORM_TYPE.EDR, SECURITY_PLATFORM_TYPE.SIEM)));

      // Act
      MigrationProcessingResult result = migration.process(tenant());

      // Assert
      assertEquals(MigrationProcessingResult.PROCESSED, result);
      Payload payload = reloadPayload(contract);
      assertEquals(Set.of(PREVENTION, DETECTION, LOGGING), Set.of(payload.getExpectations()));
      assertEquals(
          List.of(SECURITY_PLATFORM_TYPE.EDR, SECURITY_PLATFORM_TYPE.SIEM),
          payload.getExpectedSecurityPlatforms().get(LOGGING));
      InjectorContract rebuilt =
          injectorContractRepository.findById(contract.getId()).orElseThrow();
      assertTrue(
          List.of(injectorContractContentUtils.getPredefinedExpectations(rebuilt))
              .contains(LOGGING),
          "Logging must be a predefined expectation of the contract");
      assertEquals(
          MigrationProcessingResult.SKIPPED,
          migration.process(tenant()),
          "The migration runs once per tenant");
    }

    @Test
    @DisplayName("Should add the contract's Logging to an inject storing its expectations")
    void given_injectStoringDetectionAndPrevention_should_addLoggingWithoutDrift() {
      // Arrange
      InjectorContract contract =
          persistPayloadWithContract(
              Payload.PAYLOAD_SOURCE.MANUAL,
              new EXPECTATION_TYPE[] {PREVENTION, DETECTION},
              Map.of(DETECTION, List.of(SECURITY_PLATFORM_TYPE.SIEM)));
      Inject inject = persistInjectStoringContractExpectations(contract);

      // Act
      migration.process(tenant());

      // Assert
      Inject migrated = reloadInject(inject);
      JsonNode expectations = migrated.getContent().get(CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS);
      assertEquals(Set.of("PREVENTION", "DETECTION", "LOGGING"), types(expectations));
      JsonNode logging = expectation(expectations, LOGGING).orElseThrow();
      assertEquals(List.of("SIEM"), platforms(logging));
      assertEquals(100, logging.path("expectation_score").asInt());
      assertFalse(
          expectationsDriftService.hasDrift(migrated),
          "The inject must stay aligned with its contract");
    }

    @Test
    @DisplayName("Should leave the injects that need no Logging untouched")
    void given_injectsWithoutStoredExpectationsOrTrigger_should_leaveThemUntouched() {
      // Arrange
      InjectorContract contract =
          persistPayloadWithContract(
              Payload.PAYLOAD_SOURCE.MANUAL, new EXPECTATION_TYPE[] {DETECTION}, Map.of());
      Inject followingContract = persistInject(contract, null);
      ArrayNode manualOnly = mapper.createArrayNode();
      manualOnly.addObject().put("expectation_type", "MANUAL").put("expectation_score", 100);
      Inject withoutTrigger = persistInject(contract, manualOnly);

      // Act
      migration.process(tenant());

      // Assert
      assertNull(
          reloadInject(followingContract)
              .getContent()
              .get(CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS),
          "An inject storing no expectations follows its contract");
      assertEquals(
          Set.of("MANUAL"),
          types(
              reloadInject(withoutTrigger)
                  .getContent()
                  .get(CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS)));
    }

    @Test
    @DisplayName("Should leave imported payloads and payloads without Detection nor Prevention")
    void given_importedOrVulnerabilityPayload_should_leaveItUntouched() {
      // Arrange
      InjectorContract imported =
          persistPayloadWithContract(
              Payload.PAYLOAD_SOURCE.COMMUNITY, new EXPECTATION_TYPE[] {DETECTION}, Map.of());
      InjectorContract vulnerability =
          persistPayloadWithContract(
              Payload.PAYLOAD_SOURCE.MANUAL, new EXPECTATION_TYPE[] {VULNERABILITY}, Map.of());

      // Act
      migration.process(tenant());

      // Assert
      assertEquals(Set.of(DETECTION), Set.of(reloadPayload(imported).getExpectations()));
      assertEquals(Set.of(VULNERABILITY), Set.of(reloadPayload(vulnerability).getExpectations()));
    }
  }
}
