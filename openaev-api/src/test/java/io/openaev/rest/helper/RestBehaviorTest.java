package io.openaev.rest.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.openaev.config.TenantFilteringException;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springdoc.api.ErrorMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
}
