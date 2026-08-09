package io.openaev.aop.audit_log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.aop.AccessControl;
import io.openaev.database.model.Action;
import io.openaev.database.model.EventStatus;
import io.openaev.database.model.ResourceType;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

/**
 * Regression tests for the request-payload resolution of {@link AccessControlAuditLogAspect}.
 *
 * <p>Covers the Nuclei injector registration OOM: {@code InjectorApi.registerInjector} passes its
 * DTO via {@code @RequestPart} (multipart, because the endpoint also accepts an icon). The aspect
 * used to only recognise {@code @RequestBody}, so the payload fell through to the {@code signature}
 * node — which, unlike {@code input}, was not size-capped by {@code ObjectNormalizationUtils} at
 * the time (it now is). The full, uncapped contract payload was then deep-copied several times and
 * queued on the bounded audit executor, exhausting the heap.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccessControlAuditLogAspect — request payload resolution")
class AccessControlAuditLogAspectPayloadTest {

  /** Marker written into the signature node in place of the (already captured) payload. */
  private static final String PAYLOAD_PLACEHOLDER = "@RequestBody";

  private static final String PAYLOAD_NAME = "Nuclei";

  @Mock private AuditLogger auditLogger;
  @Mock private ProceedingJoinPoint joinPoint;
  @Mock private MethodSignature methodSignature;

  private AccessControlAuditLogAspect aspect;

  private ArgumentCaptor<JsonNode> inputCaptor;
  private ArgumentCaptor<JsonNode> signatureCaptor;

  /** Payload DTO standing in for {@code InjectorCreateInput}. */
  record PayloadInput(String name, String type) {}

  /** Controller-like fixture exposing the annotation shapes the aspect must understand. */
  @SuppressWarnings("unused")
  static class SampleApi {

    @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.INJECTOR)
    void createWithRequestBody(@RequestBody PayloadInput input) {}

    // Mirrors InjectorApi.registerInjector
    @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.INJECTOR)
    void createWithRequestPart(
        @RequestPart("input") PayloadInput input,
        @RequestPart("icon") Optional<MultipartFile> file) {}

    @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.INJECTOR)
    void createWithModelAttribute(@ModelAttribute PayloadInput input) {}

    @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.INJECTOR)
    void createWithFileOnly(@RequestPart("icon") MultipartFile file) {}

    @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.INJECTOR)
    void createWithServletRequest(@RequestBody PayloadInput input, HttpServletRequest request) {}

    @AccessControl(actionPerformed = Action.DELETE, resourceType = ResourceType.INJECTOR)
    void deleteWithRequestBody(@RequestBody PayloadInput input) {}
  }

  @BeforeEach
  void setup() {
    aspect = new AccessControlAuditLogAspect(auditLogger, new ObjectMapper());

    inputCaptor = ArgumentCaptor.forClass(JsonNode.class);
    signatureCaptor = ArgumentCaptor.forClass(JsonNode.class);

    lenient().when(auditLogger.isAuditLoggingEnabled()).thenReturn(true);
    lenient().when(auditLogger.isAuditLoggingValid(any())).thenReturn(true);
    lenient()
        .when(
            auditLogger.logAccessControlEvent(
                any(), any(), any(), anyString(), any(), any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(true));
  }

  /**
   * Wires the mocked join point onto the given fixture method and runs the aspect, then captures
   * the {@code input} and {@code signature} nodes handed to the audit logger.
   */
  private void invokeAspect(String methodName, String[] paramNames, Object... args)
      throws Throwable {
    Method method =
        java.util.Arrays.stream(SampleApi.class.getDeclaredMethods())
            .filter(m -> m.getName().equals(methodName))
            .findFirst()
            .orElseThrow();

    when(joinPoint.getSignature()).thenReturn(methodSignature);
    when(methodSignature.getMethod()).thenReturn(method);
    lenient().when(methodSignature.getParameterNames()).thenReturn(paramNames);
    lenient().when(methodSignature.getDeclaringTypeName()).thenReturn(SampleApi.class.getName());
    lenient().when(methodSignature.getName()).thenReturn(methodName);
    when(joinPoint.getArgs()).thenReturn(args);
    when(joinPoint.proceed()).thenReturn("ok");

    aspect.auditAround(joinPoint);

    verify(auditLogger)
        .logAccessControlEvent(
            any(AuditEventScope.class),
            any(EventStatus.class),
            any(ResourceType.class),
            anyString(),
            inputCaptor.capture(),
            any(),
            signatureCaptor.capture(),
            any());
  }

  private JsonNode signatureParam(String paramName) {
    return signatureCaptor.getValue().path("parameters").path(paramName);
  }

  @Nested
  @DisplayName("Payload annotation detection")
  class PayloadAnnotationDetection {

    @Test
    @DisplayName("@RequestPart payload is captured as input, not duplicated into signature")
    void given_requestPartPayload_should_captureInputAndPlaceholderInSignature() throws Throwable {
      // Arrange
      PayloadInput payload = new PayloadInput(PAYLOAD_NAME, "openaev_nuclei");

      // Act
      invokeAspect(
          "createWithRequestPart",
          new String[] {"input", "file"},
          payload,
          Optional.<MultipartFile>empty());

      // Assert — the payload lands in `input` (which IS size-capped downstream)
      assertThat(inputCaptor.getValue()).isNotNull();
      assertThat(inputCaptor.getValue().path("name").asText()).isEqualTo(PAYLOAD_NAME);

      // Assert — and is NOT re-serialized into the (uncapped) signature node
      assertThat(signatureParam("input").asText()).isEqualTo(PAYLOAD_PLACEHOLDER);
      assertThat(signatureCaptor.getValue().toString()).doesNotContain("openaev_nuclei");
    }

    @Test
    @DisplayName("@RequestBody payload is still captured as input (non-regression)")
    void given_requestBodyPayload_should_captureInputAndPlaceholderInSignature() throws Throwable {
      // Arrange
      PayloadInput payload = new PayloadInput(PAYLOAD_NAME, "openaev_nuclei");

      // Act
      invokeAspect("createWithRequestBody", new String[] {"input"}, payload);

      // Assert
      assertThat(inputCaptor.getValue().path("name").asText()).isEqualTo(PAYLOAD_NAME);
      assertThat(signatureParam("input").asText()).isEqualTo(PAYLOAD_PLACEHOLDER);
    }

    @Test
    @DisplayName("@ModelAttribute payload is captured as input")
    void given_modelAttributePayload_should_captureInput() throws Throwable {
      // Arrange
      PayloadInput payload = new PayloadInput(PAYLOAD_NAME, "openaev_nuclei");

      // Act
      invokeAspect("createWithModelAttribute", new String[] {"input"}, payload);

      // Assert
      assertThat(inputCaptor.getValue().path("name").asText()).isEqualTo(PAYLOAD_NAME);
      assertThat(signatureParam("input").asText()).isEqualTo(PAYLOAD_PLACEHOLDER);
    }

    @Test
    @DisplayName("a @RequestPart file is never selected as the payload")
    void given_multipartFileOnlyEndpoint_should_notCaptureFileAsInput() throws Throwable {
      // Arrange
      MultipartFile icon =
          new MockMultipartFile("icon", "icon.png", "image/png", new byte[] {1, 2, 3});

      // Act
      invokeAspect("createWithFileOnly", new String[] {"file"}, icon);

      // Assert
      assertThat(inputCaptor.getValue()).isNull();
    }
  }

  @Nested
  @DisplayName("Non-serializable arguments")
  class NonSerializableArguments {

    @Test
    @DisplayName("a MultipartFile is logged by class name, never as a base64 blob")
    void given_multipartFileArgument_should_logClassNameOnly() throws Throwable {
      // Arrange — a payload big enough that base64-serializing it would be visible
      byte[] content = new byte[4096];
      java.util.Arrays.fill(content, (byte) 'A');
      MultipartFile icon = new MockMultipartFile("icon", "icon.png", "image/png", content);

      // Act
      invokeAspect(
          "createWithRequestPart",
          new String[] {"input", "file"},
          new PayloadInput(PAYLOAD_NAME, "openaev_nuclei"),
          Optional.of(icon));

      // Assert
      assertThat(signatureParam("file").asText())
          .isEqualTo(MockMultipartFile.class.getSimpleName());
      assertThat(signatureCaptor.getValue().toString()).doesNotContain("AAAA");
    }

    @Test
    @DisplayName("an empty Optional does not break signature serialization")
    void given_emptyOptionalFile_should_serializeSignatureWithoutError() throws Throwable {
      // Arrange / Act
      invokeAspect(
          "createWithRequestPart",
          new String[] {"input", "file"},
          new PayloadInput(PAYLOAD_NAME, "openaev_nuclei"),
          Optional.<MultipartFile>empty());

      // Assert
      assertThat(signatureCaptor.getValue()).isNotNull();
      assertThat(signatureCaptor.getValue().path("parameters").has("file")).isTrue();
    }

    @Test
    @DisplayName("an HttpServletRequest argument is logged by class name")
    void given_httpServletRequestArgument_should_logClassNameOnly() throws Throwable {
      // Arrange
      HttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest();

      // Act
      invokeAspect(
          "createWithServletRequest",
          new String[] {"input", "request"},
          new PayloadInput(PAYLOAD_NAME, "openaev_nuclei"),
          request);

      // Assert
      assertThat(signatureParam("request").asText()).isEqualTo("MockHttpServletRequest");
    }
  }

  @Nested
  @DisplayName("Event scope filtering")
  class EventScopeFiltering {

    @Test
    @DisplayName("non-mutating scopes do not capture the payload as input")
    void given_deleteScope_should_notCaptureInput() throws Throwable {
      // Arrange / Act
      invokeAspect(
          "deleteWithRequestBody",
          new String[] {"input"},
          new PayloadInput(PAYLOAD_NAME, "openaev_nuclei"));

      // Assert
      assertThat(inputCaptor.getValue()).isNull();
    }
  }

  @Nested
  @DisplayName("Signature node shape")
  class SignatureNodeShape {

    @Test
    @DisplayName("the signature always carries the fully qualified method name")
    void given_anyAuditedMethod_should_includeMethodName() throws Throwable {
      // Arrange / Act
      invokeAspect(
          "createWithRequestBody",
          new String[] {"input"},
          new PayloadInput(PAYLOAD_NAME, "openaev_nuclei"));

      // Assert
      assertThat(signatureCaptor.getValue().path("method").asText())
          .isEqualTo(SampleApi.class.getName() + ".createWithRequestBody");
    }

    @Test
    @DisplayName("a null parameter name array yields an empty parameters node")
    void given_missingParameterNames_should_produceEmptyParameters() throws Throwable {
      // Arrange / Act
      invokeAspect("createWithRequestBody", null, new PayloadInput(PAYLOAD_NAME, "openaev_nuclei"));

      // Assert
      assertThat(signatureCaptor.getValue().path("parameters"))
          .isEqualTo(new ObjectMapper().createObjectNode());
    }
  }
}
