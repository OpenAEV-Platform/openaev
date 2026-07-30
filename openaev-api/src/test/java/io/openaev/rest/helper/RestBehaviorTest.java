package io.openaev.rest.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import io.openaev.config.TenantFilteringException;
import io.openaev.database.model.Filters.FilterOperator;
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
    @DisplayName("InvalidFormatException cause yields a 400 naming the offending field and value")
    void given_invalidFormatCause_should_nameFieldAndValue() {
      // GIVEN - the failure shape produced when an enum value cannot be deserialized (#6927)
      InvalidFormatException ife =
          new InvalidFormatException(
              (JsonParser) null, "Cannot deserialize", "WRONG_OP", FilterOperator.class);
      ife.prependPath(new JsonMappingException.Reference(null, "operator"));
      HttpMessageNotReadableException ex =
          new HttpMessageNotReadableException("JSON parse error", ife, null);

      // WHEN
      ResponseEntity<ErrorMessage> response = new RestBehavior().handleHttpMessageNotReadable(ex);

      // THEN
      assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
      assertNotNull(response.getBody());
      assertEquals(
          "Invalid value 'WRONG_OP' for field 'operator'", response.getBody().getMessage());
    }

    @Test
    @DisplayName("array indices are attached to their parent segment in the field path")
    void given_nestedArrayPath_should_renderIndexAttachedToParent() {
      // GIVEN - an invalid enum nested inside a filter list: filters[0].operator
      InvalidFormatException ife =
          new InvalidFormatException(
              (JsonParser) null, "Cannot deserialize", "WRONG_OP", FilterOperator.class);
      ife.prependPath(new JsonMappingException.Reference(null, "operator"));
      ife.prependPath(new JsonMappingException.Reference(null, 0));
      ife.prependPath(new JsonMappingException.Reference(null, "filters"));
      HttpMessageNotReadableException ex =
          new HttpMessageNotReadableException("JSON parse error", ife, null);

      // WHEN
      ResponseEntity<ErrorMessage> response = new RestBehavior().handleHttpMessageNotReadable(ex);

      // THEN
      assertNotNull(response.getBody());
      assertEquals(
          "Invalid value 'WRONG_OP' for field 'filters[0].operator'",
          response.getBody().getMessage());
    }

    @Test
    @DisplayName("oversized rejected value is truncated in the 400 message")
    void given_oversizedRejectedValue_should_truncateInMessage() {
      // GIVEN - a caller-supplied value far beyond the 100-char echo bound
      String hugeValue = "X".repeat(500);
      InvalidFormatException ife =
          new InvalidFormatException(
              (JsonParser) null, "Cannot deserialize", hugeValue, FilterOperator.class);
      ife.prependPath(new JsonMappingException.Reference(null, "operator"));
      HttpMessageNotReadableException ex =
          new HttpMessageNotReadableException("JSON parse error", ife, null);

      // WHEN
      ResponseEntity<ErrorMessage> response = new RestBehavior().handleHttpMessageNotReadable(ex);

      // THEN
      assertNotNull(response.getBody());
      String message = response.getBody().getMessage();
      assertTrue(message.contains("X".repeat(100) + "..."));
      assertTrue(message.length() < 200);
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
