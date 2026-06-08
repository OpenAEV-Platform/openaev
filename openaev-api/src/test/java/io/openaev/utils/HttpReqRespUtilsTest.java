package io.openaev.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.openaev.config.ThreadPoolTaskLoggerConfig.ThreadRequestContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@DisplayName("HttpReqRespUtils")
class HttpReqRespUtilsTest {

  @AfterEach
  void cleanup() {
    RequestContextHolder.resetRequestAttributes();
    ThreadRequestContextHolder.clear();
  }

  @Nested
  @DisplayName("getClientIpAddressFromHeaders")
  class GetClientIpAddressFromHeaders {

    @Test
    void given_forwardedForWithMultipleIps_should_returnFirstIp() {
      // Arrange
      Map<String, String> headers = Map.of("X-Forwarded-For", "203.0.113.10, 70.41.3.18");

      // Act
      String ip = HttpReqRespUtils.getClientIpAddressFromHeaders(headers);

      // Assert
      assertThat(ip).isEqualTo("203.0.113.10");
    }

    @Test
    void given_unknownOrEmptyHeaders_should_returnNull() {
      // Arrange
      Map<String, String> headers =
          Map.of("X-Forwarded-For", "unknown", "Proxy-Client-IP", "", "X-Real-IP", "unknown");

      // Act
      String ip = HttpReqRespUtils.getClientIpAddressFromHeaders(headers);

      // Assert
      assertThat(ip).isNull();
    }

    @Test
    void given_caseInsensitiveHeader_should_returnIp() {
      // Arrange
      Map<String, String> headers = Map.of("x-forwarded-for", "198.51.100.22");

      // Act
      String ip = HttpReqRespUtils.getClientIpAddressFromHeaders(headers);

      // Assert
      assertThat(ip).isEqualTo("198.51.100.22");
    }
  }

  @Nested
  @DisplayName("extractHeader")
  class ExtractHeader {

    @Test
    void given_mixedCaseHeaderName_should_extractValueIgnoringCase() {
      // Arrange
      Map<String, String> headers = Map.of("X-Correlation-Id", "corr-123");

      // Act
      String value = HttpReqRespUtils.extractHeader(headers, "x-correlation-id");

      // Assert
      assertThat(value).isEqualTo("corr-123");
    }

    @Test
    void given_missingHeader_should_returnNull() {
      // Arrange
      Map<String, String> headers = Map.of("X-Correlation-Id", "corr-123");

      // Act
      String value = HttpReqRespUtils.extractHeader(headers, "x-request-id");

      // Assert
      assertThat(value).isNull();
    }
  }

  @Nested
  @DisplayName("extractHeaders")
  class ExtractHeaders {

    @Test
    void given_requestWithHeaders_should_returnAllHeadersMap() {
      // Arrange
      HttpServletRequest request = mock(HttpServletRequest.class);
      Enumeration<String> names = Collections.enumeration(java.util.List.of("h1", "h2"));
      when(request.getHeaderNames()).thenReturn(names);
      when(request.getHeader("h1")).thenReturn("v1");
      when(request.getHeader("h2")).thenReturn("v2");

      // Act
      Map<String, String> headers = HttpReqRespUtils.extractHeaders(request);

      // Assert
      assertThat(headers).containsEntry("h1", "v1").containsEntry("h2", "v2");
    }

    @Test
    void given_requestThrowsIllegalState_should_fallbackToThreadContextHeaders() {
      // Arrange
      HttpServletRequest request = mock(HttpServletRequest.class);
      when(request.getHeaderNames()).thenThrow(new IllegalStateException("recycled"));
      ThreadRequestContextHolder.setRequestContextData(
          new ThreadRequestContextHolder.RequestContextData(
              Map.of("x-fallback", "fb-value"), null, null, null, null, null));

      // Act
      Map<String, String> headers = HttpReqRespUtils.extractHeaders(request);

      // Assert
      assertThat(headers).containsEntry("x-fallback", "fb-value");
    }
  }

  @Nested
  @DisplayName("extractMethod")
  class ExtractMethod {

    @Test
    void given_requestMethod_should_returnMethod() {
      // Arrange
      HttpServletRequest request = mock(HttpServletRequest.class);
      when(request.getMethod()).thenReturn("PUT");

      // Act
      String method = HttpReqRespUtils.extractMethod(request);

      // Assert
      assertThat(method).isEqualTo("PUT");
    }

    @Test
    void given_requestThrowsIllegalState_should_fallbackToThreadContextMethod() {
      // Arrange
      HttpServletRequest request = mock(HttpServletRequest.class);
      when(request.getMethod()).thenThrow(new IllegalStateException("recycled"));
      ThreadRequestContextHolder.setRequestContextData(
          new ThreadRequestContextHolder.RequestContextData(null, null, "PATCH", null, null, null));

      // Act
      String method = HttpReqRespUtils.extractMethod(request);

      // Assert
      assertThat(method).isEqualTo("PATCH");
    }
  }

  @Nested
  @DisplayName("getCurrentRequest")
  class GetCurrentRequest {

    @Test
    void given_requestAttributesPresent_should_returnCurrentRequest() {
      // Arrange
      HttpServletRequest request = mock(HttpServletRequest.class);
      RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

      // Act
      HttpServletRequest currentRequest = HttpReqRespUtils.getCurrentRequest();

      // Assert
      assertThat(currentRequest).isSameAs(request);
    }

    @Test
    void given_noRequestAttributes_should_returnNull() {
      // Arrange
      RequestContextHolder.resetRequestAttributes();

      // Act
      HttpServletRequest currentRequest = HttpReqRespUtils.getCurrentRequest();

      // Assert
      assertThat(currentRequest).isNull();
    }
  }

  @Nested
  @DisplayName("getClientIpAddressIfServletRequestExist")
  class GetClientIpAddressIfServletRequestExist {

    @Test
    void given_noRequest_should_fallbackToThreadContextRemoteAddress() {
      // Arrange
      ThreadRequestContextHolder.setRequestContextData(
          new ThreadRequestContextHolder.RequestContextData(
              Map.of(), "10.20.30.40", null, null, null, null));

      // Act
      String ip = HttpReqRespUtils.getClientIpAddressIfServletRequestExist();

      // Assert
      assertThat(ip).isEqualTo("10.20.30.40");
    }

    @Test
    void given_noRequestAndNoThreadContext_should_returnDefaultIp() {
      // Arrange
      RequestContextHolder.resetRequestAttributes();
      ThreadRequestContextHolder.clear();

      // Act
      String ip = HttpReqRespUtils.getClientIpAddressIfServletRequestExist();

      // Assert
      assertThat(ip).isEqualTo("0.0.0.0");
    }
  }
}
