package io.openaev.aop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import io.openaev.config.OpenAEVPrincipal;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.PreviewFeatureService;
import io.openaev.api.url_access_token.UrlAccessTokenService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;

@ExtendWith(MockitoExtension.class)
@DisplayName("UrlAccessControlAspect")
class UrlAccessControlAspectTest {

  @Mock private UrlAccessTokenService urlAccessTokenService;
  @Mock private PreviewFeatureService previewFeatureService;
  @InjectMocks private UrlAccessControlAspect aspect;

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    RequestContextHolder.resetRequestAttributes();
  }

  @Nested
  @DisplayName("Authenticated users")
  class AuthenticatedUsers {

    @Test
    @DisplayName(
        "given_classically_authenticated_user_when_url_access_control_is_applied_should_bypass_url_token_validation")
    void given_classically_authenticated_user_when_url_access_control_is_applied_should_bypass_url_token_validation()
        throws Throwable {
      // -- Arrange --
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.URL_ACCESS_TOKEN)).thenReturn(true);
      ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
      when(joinPoint.proceed()).thenReturn("ok");

      OpenAEVPrincipal principal = mock(OpenAEVPrincipal.class);
      when(principal.getId()).thenReturn("user-1");
      Authentication authentication = mock(Authentication.class);
      when(authentication.getPrincipal()).thenReturn(principal);
      SecurityContext context = SecurityContextHolder.createEmptyContext();
      context.setAuthentication(authentication);
      SecurityContextHolder.setContext(context);

      // -- Act --
      Object result = aspect.validateUrlAccess(joinPoint);

      // -- Assert --
      assertThat(result).isEqualTo("ok");
      verify(urlAccessTokenService, never()).validateToken(anyString(), any(), any());
      verify(joinPoint).proceed();
    }
  }

  @Nested
  @DisplayName("Anonymous users")
  class AnonymousUsers {

    @Test
    @DisplayName(
        "given_anonymous_user_without_request_context_when_url_access_control_is_applied_should_fail_with_illegal_state")
    void given_anonymous_user_without_request_context_when_url_access_control_is_applied_should_fail_with_illegal_state() {
      // -- Arrange --
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.URL_ACCESS_TOKEN)).thenReturn(true);
      ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);

      // -- Act & Assert --
      assertThatThrownBy(() -> aspect.validateUrlAccess(joinPoint))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("no HTTP request");
    }
  }
}


