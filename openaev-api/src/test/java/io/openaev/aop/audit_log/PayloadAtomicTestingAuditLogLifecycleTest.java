package io.openaev.aop.audit_log;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.collectors.expectations_expiration_manager.ExpectationsExpirationManagerCollector;
import io.openaev.context.TenantContext;
import io.openaev.engine.model.log.LogEvent;
import io.openaev.injectors.email.service.ImapService;
import io.openaev.integration.impl.injectors.email.EmailInjectorIntegrationFactory;
import io.openaev.integration.impl.injectors.openaev.OpenaevInjectorIntegrationFactory;
import io.openaev.rest.atomic_testing.AtomicTestingApi;
import io.openaev.rest.atomic_testing.form.AtomicTestingInput;
import io.openaev.rest.payload.form.PayloadCreateInput;
import io.openaev.service.LogService;
import io.openaev.utils.fixtures.DomainFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.PayloadInputFixture;
import io.openaev.utils.fixtures.composers.DomainComposer;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.log.dispatcher.AuditLogTransportDispatcherUtils;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@TestPropertySource(properties = {"openaev.audit-logs.transports=console"})
class PayloadAtomicTestingAuditLogLifecycleTest extends IntegrationTest {

  private static final String PAYLOAD_URI = "/api/payloads";

  @Autowired private MockMvc mvc;
  @Autowired private DomainComposer domainComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private EmailInjectorIntegrationFactory emailInjectorIntegrationFactory;
  @Autowired private OpenaevInjectorIntegrationFactory openaevInjectorIntegrationFactory;

  @MockitoBean private ImapService imapService;

  @MockitoBean
  private ExpectationsExpirationManagerCollector expectationsExpirationManagerCollector;

  @MockitoSpyBean private AuditLogger auditLogger;

  @MockitoSpyBean private LogService logService;

  @MockitoSpyBean private AuditLogTransportDispatcherUtils auditLogTransportDispatcherUtils;

  // Ensure each test starts with clean spies and deterministic audit-enablement behavior.
  @BeforeEach
  void beforeEach() {
    reset(auditLogger);
    reset(logService);
    reset(auditLogTransportDispatcherUtils);
    // Force audit checks to pass so lifecycle actions are observable through dispatched events.
    doReturn(true).when(auditLogger).isAuditLoggingEnabled();
    doReturn(true).when(auditLogger).isAuditLoggingValid(any());
    doReturn(true).when(logService).isEnabled();
  }

  @Nested
  @DisplayName("Payload + Atomic testing lifecycle")
  class PayloadAtomicTestingLifecycle {

    @Test
    @WithMockUser(isAdmin = true)
    // Verifies end-to-end audit logging for payload + atomic testing lifecycle actions.
    void given_payloadAndAtomicTestingLifecycle_should_logExpectedAuditEvents() throws Exception {
      // Arrange
      // Register built-in injector under the active mock user context for deterministic tenant
      // scope.
      emailInjectorIntegrationFactory.registerConnectorForTenant(TenantContext.getCurrentTenant());
      openaevInjectorIntegrationFactory.registerConnectorForTenant(
          TenantContext.getCurrentTenant());

      // Use unique labels to avoid collisions with existing data in integration environments.
      String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
      String payloadName = "audit-payload-" + uniqueSuffix;
      String injectTitle = "audit-atomic-testing-" + uniqueSuffix;

      // Create referenced entities required by payload/atomic-testing inputs.
      String domainId =
          domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get().getId();
      String assetId =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist().get().getId();

      // Build a payload create request and inject a unique payload name for assertion.
      PayloadCreateInput payloadInput =
          PayloadInputFixture.createDefaultPayloadCreateInputForCommandLine(List.of(domainId));
      payloadInput.setName(payloadName);

      // Act
      // 1) Create payload and keep the returned payload id to link the atomic testing input.
      String payloadResponse =
          mvc.perform(
                  post(PAYLOAD_URI)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(payloadInput))
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String payloadId = JsonPath.read(payloadResponse, "$.payload_id");

      // 2) Create atomic testing linked to the previously created payload.
      AtomicTestingInput atomicCreateInput = InjectFixture.createAtomicTesting(injectTitle, null);
      atomicCreateInput.getContent().put("payload_id", payloadId);

      String atomicCreateResponse =
          mvc.perform(
                  post(AtomicTestingApi.ATOMIC_TESTING_URI)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(atomicCreateInput))
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String atomicTestingId = JsonPath.read(atomicCreateResponse, "$.inject_id");

      // 3) Update atomic testing to add one asset.
      AtomicTestingInput addAssetsInput = InjectFixture.createAtomicTesting(injectTitle, null);
      addAssetsInput.setAssets(List.of(assetId));
      addAssetsInput.getContent().put("payload_id", payloadId);

      mvc.perform(
              put(AtomicTestingApi.ATOMIC_TESTING_URI + "/" + atomicTestingId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(addAssetsInput))
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      // 4) Update atomic testing again to remove all assets.
      AtomicTestingInput removeAssetsInput = InjectFixture.createAtomicTesting(injectTitle, null);
      removeAssetsInput.setAssets(List.of());
      removeAssetsInput.getContent().put("payload_id", payloadId);

      mvc.perform(
              put(AtomicTestingApi.ATOMIC_TESTING_URI + "/" + atomicTestingId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(removeAssetsInput))
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      // 5) Launch atomic testing to trigger a status change event.
      mvc.perform(
              post(AtomicTestingApi.ATOMIC_TESTING_URI + "/" + atomicTestingId + "/launch")
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      // Assert
      // Capture all dispatched audit events and ensure we observed at least the expected 5 actions.
      ArgumentCaptor<LogEvent> eventCaptor = ArgumentCaptor.forClass(LogEvent.class);
      verify(auditLogTransportDispatcherUtils, timeout(10000).atLeast(5))
          .dispatch(eventCaptor.capture(), any());

      List<LogEvent> events = eventCaptor.getAllValues();
      assertThat(events).hasSizeGreaterThanOrEqualTo(5);

      // Validate payload creation event: mutation/create for payload with expected payload_name.
      LogEvent payloadCreateEvent =
          findRequiredEvent(
              events,
              event ->
                  "mutation".equals(event.getEventType())
                      && "create".equals(event.getEventScope())
                      && contextEntityType(event).contains("Payload")
                      && contextInputValue(event, "payload_name")
                          .filter(payloadName::equals)
                          .isPresent(),
              "payload create event");

      // Validate atomic testing creation event: mutation/create with expected inject_title.
      LogEvent atomicCreateEvent =
          findRequiredEvent(
              events,
              event ->
                  "mutation".equals(event.getEventType())
                      && "create".equals(event.getEventScope())
                      && contextInputValue(event, "inject_title")
                          .filter(injectTitle::equals)
                          .isPresent(),
              "atomic testing create event");

      // Validate add-assets update event by checking inject_assets contains the created asset id.
      LogEvent addAssetEvent =
          findRequiredEvent(
              events,
              event ->
                  "mutation".equals(event.getEventType())
                      && "update".equals(event.getEventScope())
                      && contextInputList(event, "inject_assets").contains(assetId),
              "atomic testing add assets event");

      // Validate remove-assets update event by checking inject_assets becomes empty.
      LogEvent removeAssetEvent =
          findRequiredEvent(
              events,
              event ->
                  "mutation".equals(event.getEventType())
                      && "update".equals(event.getEventScope())
                      && contextInputList(event, "inject_assets").isEmpty(),
              "atomic testing remove assets event");

      // Validate launch event emits a mutation with status_change scope.
      LogEvent launchEvent =
          findRequiredEvent(
              events,
              event ->
                  "mutation".equals(event.getEventType())
                      && "status_change".equals(event.getEventScope()),
              "atomic testing launch event");

      // We assert presence and content of each lifecycle event, but not strict ordering.
      // Audit dispatch is asynchronous and can reorder under load.
      assertThat(
              List.of(
                  payloadCreateEvent,
                  atomicCreateEvent,
                  addAssetEvent,
                  removeAssetEvent,
                  launchEvent))
          .extracting(LogEvent::getEventType)
          .containsOnly("mutation");
      assertThat(
              List.of(
                  payloadCreateEvent,
                  atomicCreateEvent,
                  addAssetEvent,
                  removeAssetEvent,
                  launchEvent))
          .extracting(LogEvent::getEventScope)
          .containsExactlyInAnyOrder("create", "create", "update", "update", "status_change");

      // Child resource events (assets) should link to the atomic testing.
      assertThat(resolveParentLink(addAssetEvent)).isEqualTo(atomicTestingId);
      assertThat(resolveParentLink(removeAssetEvent)).isEqualTo(atomicTestingId);
    }
  }

  // Returns the first event matching the predicate, or fails with a clear missing-event message.
  private LogEvent findRequiredEvent(
      List<LogEvent> events, java.util.function.Predicate<LogEvent> predicate, String description) {
    return events.stream()
        .filter(Objects::nonNull)
        .filter(predicate)
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing expected " + description));
  }

  // Reads the logical entity type from event context_data (empty string when unavailable).
  private String contextEntityType(LogEvent event) {
    Object entityType = contextValue(event, "entity_type");
    return entityType != null ? entityType.toString() : "";
  }

  // Generic accessor for context_data keys with null-safety.
  private Object contextValue(LogEvent event, String key) {
    Map<String, Object> context = event.getContextData();
    if (context == null) {
      return null;
    }
    return context.get(key);
  }

  @SuppressWarnings("unchecked")
  // Resolves the atomic testing id (inject_id) from context_data.output.
  private String resolveParentLink(LogEvent event) {
    Object output = contextValue(event, "output");
    if (!(output instanceof Map<?, ?> outputMap)) {
      return null;
    }

    Object injectId = ((Map<String, Object>) outputMap).get("inject_id");
    return injectId != null ? injectId.toString() : null;
  }

  @SuppressWarnings("unchecked")
  // Reads a single key from context_data.input as Optional<String>.
  private Optional<String> contextInputValue(LogEvent event, String key) {
    Object input = contextValue(event, "input");
    if (!(input instanceof Map<?, ?> inputMap)) {
      return Optional.empty();
    }
    Object value = ((Map<String, Object>) inputMap).get(key);
    return value != null ? Optional.of(value.toString()) : Optional.empty();
  }

  @SuppressWarnings("unchecked")
  // Reads a list key from context_data.input and normalizes all entries to String.
  private List<String> contextInputList(LogEvent event, String key) {
    Object input = contextValue(event, "input");
    if (!(input instanceof Map<?, ?> inputMap)) {
      return List.of();
    }

    Object value = ((Map<String, Object>) inputMap).get(key);
    if (!(value instanceof List<?> values)) {
      return List.of();
    }

    return values.stream().map(String::valueOf).toList();
  }
}
