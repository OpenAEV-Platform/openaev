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
import io.openaev.engine.model.log.LogEvent;
import io.openaev.injectors.email.service.ImapService;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

  @MockitoBean private ImapService imapService;

  @MockitoSpyBean private AuditLogger auditLogger;

  @MockitoSpyBean private LogService logService;

  @MockitoSpyBean private AuditLogTransportDispatcherUtils auditLogTransportDispatcherUtils;

  @BeforeEach
  void beforeEach() {
    reset(auditLogger);
    reset(logService);
    reset(auditLogTransportDispatcherUtils);
    doReturn(true).when(auditLogger).isAuditLoggingEnabled();
    doReturn(true).when(auditLogger).isAuditLoggingValid(any());
    doReturn(true).when(logService).isEnabled();
  }

  @Nested
  @DisplayName("Payload + Atomic testing lifecycle")
  class PayloadAtomicTestingLifecycle {

    @Test
    @WithMockUser(isAdmin = true)
    void given_payloadAndAtomicTestingLifecycle_should_logExpectedAuditEvents() throws Exception {
      // Arrange
      String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
      String payloadName = "audit-payload-" + uniqueSuffix;
      String injectTitle = "audit-atomic-testing-" + uniqueSuffix;

      String domainId =
          domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get().getId();
      String assetId =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist().get().getId();

      PayloadCreateInput payloadInput =
          PayloadInputFixture.createDefaultPayloadCreateInputForCommandLine(List.of(domainId));
      payloadInput.setName(payloadName);

      // Act
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

      AtomicTestingInput addAssetsInput = InjectFixture.createAtomicTesting(injectTitle, null);
      addAssetsInput.setAssets(List.of(assetId));
      addAssetsInput.getContent().put("payload_id", payloadId);

      mvc.perform(
              put(AtomicTestingApi.ATOMIC_TESTING_URI + "/" + atomicTestingId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(addAssetsInput))
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      AtomicTestingInput removeAssetsInput = InjectFixture.createAtomicTesting(injectTitle, null);
      removeAssetsInput.setAssets(List.of());
      removeAssetsInput.getContent().put("payload_id", payloadId);

      mvc.perform(
              put(AtomicTestingApi.ATOMIC_TESTING_URI + "/" + atomicTestingId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(removeAssetsInput))
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      mvc.perform(
              post(AtomicTestingApi.ATOMIC_TESTING_URI + "/" + atomicTestingId + "/launch")
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      // Assert
      ArgumentCaptor<LogEvent> eventCaptor = ArgumentCaptor.forClass(LogEvent.class);
      verify(auditLogTransportDispatcherUtils, timeout(5000).atLeast(5))
          .dispatch(eventCaptor.capture(), any());

      List<LogEvent> events = eventCaptor.getAllValues();
      assertThat(events).hasSizeGreaterThanOrEqualTo(5);

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

      LogEvent addAssetEvent =
          findRequiredEvent(
              events,
              event ->
                  "mutation".equals(event.getEventType())
                      && "update".equals(event.getEventScope())
                      && contextInputList(event, "inject_assets").contains(assetId),
              "atomic testing add assets event");

      LogEvent removeAssetEvent =
          findRequiredEvent(
              events,
              event ->
                  "mutation".equals(event.getEventType())
                      && "update".equals(event.getEventScope())
                      && contextInputList(event, "inject_assets").isEmpty(),
              "atomic testing remove assets event");

      LogEvent launchEvent =
          findRequiredEvent(
              events,
              event ->
                  "mutation".equals(event.getEventType())
                      && "status_change".equals(event.getEventScope()),
              "atomic testing launch event");

      List<LogEvent> lifecycleEvents =
          List.of(
                  payloadCreateEvent,
                  atomicCreateEvent,
                  addAssetEvent,
                  removeAssetEvent,
                  launchEvent)
              .stream()
              .sorted(
                  Comparator.comparing(
                      LogEvent::getTimestamp, Comparator.nullsLast(Comparator.naturalOrder())))
              .toList();

      assertThat(lifecycleEvents).extracting(LogEvent::getEventType).containsOnly("mutation");
      assertThat(lifecycleEvents)
          .extracting(LogEvent::getEventScope)
          .containsExactly("create", "create", "update", "update", "status_change");

      // Child resource events (assets) should link to the atomic testing.
      assertThat(resolveParentLink(addAssetEvent)).isEqualTo(atomicTestingId);
      assertThat(resolveParentLink(removeAssetEvent)).isEqualTo(atomicTestingId);
    }
  }

  private LogEvent findRequiredEvent(
      List<LogEvent> events, java.util.function.Predicate<LogEvent> predicate, String description) {
    return events.stream()
        .filter(Objects::nonNull)
        .filter(predicate)
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing expected " + description));
  }

  private String contextEntityType(LogEvent event) {
    Object entityType = contextValue(event, "entity_type");
    return entityType != null ? entityType.toString() : "";
  }

  private Object contextValue(LogEvent event, String key) {
    Map<String, Object> context = event.getContextData();
    if (context == null) {
      return null;
    }
    return context.get(key);
  }

  @SuppressWarnings("unchecked")
  private String resolveParentLink(LogEvent event) {
    Object parentId = contextValue(event, "parent_id");
    if (parentId != null) {
      return parentId.toString();
    }

    Object output = contextValue(event, "output");
    if (!(output instanceof Map<?, ?> outputMap)) {
      return null;
    }

    Object injectId = ((Map<String, Object>) outputMap).get("inject_id");
    return injectId != null ? injectId.toString() : null;
  }

  @SuppressWarnings("unchecked")
  private java.util.Optional<String> contextInputValue(LogEvent event, String key) {
    Object input = contextValue(event, "input");
    if (!(input instanceof Map<?, ?> inputMap)) {
      return java.util.Optional.empty();
    }
    Object value = ((Map<String, Object>) inputMap).get(key);
    return value != null ? java.util.Optional.of(value.toString()) : java.util.Optional.empty();
  }

  @SuppressWarnings("unchecked")
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
