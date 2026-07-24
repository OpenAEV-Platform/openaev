package io.openaev.rest.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.config.TenantFilteringException;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springdoc.api.ErrorMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;

@DisplayName("RestBehavior exception mapping")
class RestBehaviorTest {

  @Test
  @DisplayName("a tenant-filtering refusal maps to 500 with a clear code")
  void tenantFilteringRefusalMapsToClear500() {
    ResponseEntity<ErrorMessage> response =
        new RestBehavior().handleTenantFilteringException(new TenantFilteringException("refused"));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    assertEquals("TENANT_FILTERING_REFUSED", response.getBody().getMessage());
  }

  @Test
  @DisplayName("the handler is resolved even when the refusal is wrapped (Hibernate wraps it)")
  void handlerResolvedThroughWrappedCause() {
    // Uses the resolver Spring MVC itself uses, so this proves the cause-chain resolution rather
    // than assuming it: a TenantFilteringException nested under a wrapper still selects the
    // handler.
    Method resolved =
        new ExceptionHandlerMethodResolver(RestBehavior.class)
            .resolveMethodByThrowable(
                new RuntimeException(
                    "wrapped by the persistence layer", new TenantFilteringException("refused")));

    assertEquals("handleTenantFilteringException", resolved.getName());
  }

  @Nested
  @DisplayName("HttpMessageNotReadableException handling")
  class HttpMessageNotReadableHandling {

    @Test
    @DisplayName("plain deserialization failure returns structured 400 with generic message")
    void given_plainDeserializationFailure_should_return400WithGenericMessage() {
      // GIVEN
      HttpMessageNotReadableException ex =
          new HttpMessageNotReadableException("JSON parse error", (Throwable) null, null);

      // WHEN
      ResponseEntity<ErrorMessage> response = new RestBehavior().handleHttpMessageNotReadable(ex);

      // THEN
      assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
      assertNotNull(response.getBody());
      assertEquals("Malformed or unreadable request body", response.getBody().getMessage());
    }

    @Test
    @DisplayName("handler is registered and resolved for HttpMessageNotReadableException")
    void given_httpMessageNotReadableException_should_resolveCorrectHandler() {
      // GIVEN
      ExceptionHandlerMethodResolver resolver =
          new ExceptionHandlerMethodResolver(RestBehavior.class);

      // WHEN
      Method resolved =
          resolver.resolveMethodByThrowable(
              new HttpMessageNotReadableException("JSON parse error", (Throwable) null, null));

      // THEN
      assertNotNull(resolved);
      assertEquals("handleHttpMessageNotReadable", resolved.getName());
    }

    @Test
    @DisplayName(
        "deserialization failure body includes 'Malformed' so callers can distinguish from "
            + "validation errors")
    void given_deserializationFailure_should_containDiagnosticKeyword() {
      // GIVEN
      HttpMessageNotReadableException ex =
          new HttpMessageNotReadableException("unexpected token", (Throwable) null, null);

      // WHEN
      ResponseEntity<ErrorMessage> response = new RestBehavior().handleHttpMessageNotReadable(ex);

      // THEN
      assertNotNull(response.getBody());
      assertTrue(response.getBody().getMessage().contains("Malformed"));
    }
  }
}
