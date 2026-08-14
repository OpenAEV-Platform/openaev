package io.openaev.aop;

import static io.openaev.api.url_access_token.UrlAccessTokenApi.URL_ACCESS_COOKIE_NAME;
import static io.openaev.api.url_access_token.UrlAccessTokenService.INVALID_TOKEN_MESSAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import io.openaev.api.url_access_token.UrlAccessTokenService;
import io.openaev.config.OpenAEVPrincipal;
import io.openaev.database.model.UrlAccessToken;
import io.openaev.database.model.User;
import io.openaev.service.PreviewFeatureService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

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

  // -- Helpers --

  private void setUpRequestContext(Cookie... cookies) {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getCookies()).thenReturn(cookies.length == 0 ? null : cookies);
    ServletRequestAttributes attrs = new ServletRequestAttributes(request);
    RequestContextHolder.setRequestAttributes(attrs);
  }

  private Cookie urlAccessCookie(String value) {
    return new Cookie(URL_ACCESS_COOKIE_NAME, value);
  }

  // -- Authenticated users --

  @Nested
  @DisplayName("Authenticated users")
  class AuthenticatedUsers {

    @Test
    @DisplayName(
        "Classically authenticated user when UrlAccessControl is applied should bypass url token validation")
    void
        given_classically_authenticated_user_when_url_access_control_is_applied_should_bypass_url_token_validation()
            throws Throwable {
      // -- Arrange --
      ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
      when(joinPoint.proceed()).thenReturn("ok");

      OpenAEVPrincipal principal = mock(OpenAEVPrincipal.class);
      when(principal.getId()).thenReturn("user-1");
      Authentication authentication = mock(Authentication.class);
      when(authentication.getPrincipal()).thenReturn(principal);
      SecurityContext context = SecurityContextHolder.createEmptyContext();
      context.setAuthentication(authentication);
      SecurityContextHolder.setContext(context);

      UrlAccessControl annotation = mock(UrlAccessControl.class);

      // -- Act --
      Object result = aspect.validateUrlAccess(joinPoint, annotation);

      // -- Assert --
      assertThat(result).isEqualTo("ok");
      verify(urlAccessTokenService, never()).validateToken(anyString(), any());
      verify(joinPoint).proceed();
    }
  }

  // -- Anonymous users --

  @Nested
  @DisplayName("Anonymous users")
  class AnonymousUsers {

    @Test
    @DisplayName(
        "Anonymous user without request context when UrlAccess control is applied should fail with illegal state")
    void
        given_anonymous_user_without_request_context_when_url_access_control_is_applied_should_fail_with_illegal_state() {
      // -- Arrange --
      ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
      UrlAccessControl annotation = mock(UrlAccessControl.class);

      // -- Act & Assert --
      assertThatThrownBy(() -> aspect.validateUrlAccess(joinPoint, annotation))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("no HTTP request");
    }

    @Test
    @DisplayName("When no cookies in request when UrlAccessControl is applied should return 401")
    void given_no_cookies_in_request_when_url_access_control_is_applied_should_return_401() {
      // -- Arrange --
      setUpRequestContext(); // no cookies → getCookies() returns null
      ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
      UrlAccessControl annotation = mock(UrlAccessControl.class);

      // -- Act & Assert --
      assertThatThrownBy(() -> aspect.validateUrlAccess(joinPoint, annotation))
          .isInstanceOf(ResponseStatusException.class)
          .satisfies(
              ex ->
                  assertThat(((ResponseStatusException) ex).getStatusCode())
                      .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    @DisplayName("Given cookies without token when UrlAccessControl is applied should return 401")
    void
        given_cookies_without_url_access_token_when_url_access_control_is_applied_should_return_401() {
      // -- Arrange --
      HttpServletRequest request = mock(HttpServletRequest.class);
      when(request.getCookies()).thenReturn(new Cookie[] {new Cookie("other_cookie", "value")});
      RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

      ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
      UrlAccessControl annotation = mock(UrlAccessControl.class);

      // -- Act & Assert --
      assertThatThrownBy(() -> aspect.validateUrlAccess(joinPoint, annotation))
          .isInstanceOf(ResponseStatusException.class)
          .satisfies(
              ex ->
                  assertThat(((ResponseStatusException) ex).getStatusCode())
                      .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    @DisplayName("Invalid token in cookie when UrlAccessControl is applied should return 401")
    void given_invalid_token_in_cookie_when_url_access_control_is_applied_should_return_401() {
      // -- Arrange --
      setUpRequestContext(urlAccessCookie("bad-token"));

      ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
      MethodSignature signature = mock(MethodSignature.class);
      when(joinPoint.getSignature()).thenReturn(signature);
      when(signature.getParameterNames()).thenReturn(new String[] {});
      when(joinPoint.getArgs()).thenReturn(new Object[] {});

      UrlAccessControl annotation = mock(UrlAccessControl.class);
      when(annotation.exerciseId()).thenReturn("");

      when(urlAccessTokenService.validateToken("bad-token", null))
          .thenThrow(new AccessDeniedException(INVALID_TOKEN_MESSAGE));

      // -- Act & Assert --
      assertThatThrownBy(() -> aspect.validateUrlAccess(joinPoint, annotation))
          .isInstanceOf(ResponseStatusException.class)
          .satisfies(
              ex -> {
                assertThat(((ResponseStatusException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
                assertThat(((ResponseStatusException) ex).getReason())
                    .isEqualTo(INVALID_TOKEN_MESSAGE);
              });
    }

    @Test
    @DisplayName(
        "Valid token without userId param when UrlAccessControl is applied should proceed with original args")
    void
        given_valid_token_without_userId_param_when_url_access_control_is_applied_should_proceed_with_original_args()
            throws Throwable {
      // -- Arrange --
      setUpRequestContext(urlAccessCookie("valid-token"));

      ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
      MethodSignature signature = mock(MethodSignature.class);
      when(joinPoint.getSignature()).thenReturn(signature);
      when(signature.getParameterNames()).thenReturn(new String[] {"exerciseId"});
      Object[] args = new Object[] {"exercise-42"};
      when(joinPoint.getArgs()).thenReturn(args);
      when(joinPoint.proceed(args)).thenReturn("response");

      UrlAccessControl annotation = mock(UrlAccessControl.class);
      when(annotation.exerciseId()).thenReturn("#exerciseId");
      when(annotation.userId()).thenReturn("");

      UrlAccessToken token = mockToken("resolved-user-id");
      when(urlAccessTokenService.validateToken("valid-token", "exercise-42")).thenReturn(token);

      // -- Act --
      Object result = aspect.validateUrlAccess(joinPoint, annotation);

      // -- Assert --
      assertThat(result).isEqualTo("response");
      verify(joinPoint).proceed(args);
    }

    @Test
    @DisplayName(
        "Valid token with userId param when UrlAccessControl is applied should inject userId into args")
    void
        given_valid_token_with_userId_param_when_url_access_control_is_applied_should_inject_userId_into_args()
            throws Throwable {
      // -- Arrange --
      setUpRequestContext(urlAccessCookie("valid-token"));

      ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
      MethodSignature signature = mock(MethodSignature.class);
      when(joinPoint.getSignature()).thenReturn(signature);
      when(signature.getParameterNames()).thenReturn(new String[] {"exerciseId", "userId"});
      when(joinPoint.getArgs()).thenReturn(new Object[] {"exercise-1", Optional.empty()});

      UrlAccessControl annotation = mock(UrlAccessControl.class);
      when(annotation.exerciseId()).thenReturn("#exerciseId");
      when(annotation.userId()).thenReturn("#userId");

      UrlAccessToken token = mockToken("injected-user-id");
      when(urlAccessTokenService.validateToken("valid-token", "exercise-1")).thenReturn(token);

      ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
      when(joinPoint.proceed(argsCaptor.capture())).thenReturn("injected");

      // -- Act --
      Object result = aspect.validateUrlAccess(joinPoint, annotation);

      // -- Assert --
      assertThat(result).isEqualTo("injected");
      Object[] capturedArgs = argsCaptor.getValue();
      assertThat(capturedArgs[0]).isEqualTo("exercise-1");
      assertThat(capturedArgs[1]).isEqualTo(Optional.of("injected-user-id"));
    }

    @Test
    @DisplayName(
        "Valid token with exercise and userId params when UrlAccessControl is applied should pass exerciseId to validation")
    void
        given_valid_token_with_exercise_and_userId_params_when_url_access_control_is_applied_should_pass_exerciseId_to_validation()
            throws Throwable {
      // -- Arrange --
      setUpRequestContext(urlAccessCookie("my-token"));

      ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
      MethodSignature signature = mock(MethodSignature.class);
      when(joinPoint.getSignature()).thenReturn(signature);
      when(signature.getParameterNames()).thenReturn(new String[] {"userId", "exerciseId"});
      when(joinPoint.getArgs()).thenReturn(new Object[] {Optional.empty(), "exercise-99"});

      UrlAccessControl annotation = mock(UrlAccessControl.class);
      when(annotation.exerciseId()).thenReturn("#exerciseId");
      when(annotation.userId()).thenReturn("#userId");

      UrlAccessToken token = mockToken("user-xyz");
      when(urlAccessTokenService.validateToken("my-token", "exercise-99")).thenReturn(token);
      when(joinPoint.proceed(any(Object[].class))).thenReturn("done");

      // -- Act --
      aspect.validateUrlAccess(joinPoint, annotation);

      // -- Assert --
      verify(urlAccessTokenService).validateToken("my-token", "exercise-99");
    }

    // -- Private helpers --

    private UrlAccessToken mockToken(String userId) {
      UrlAccessToken token = mock(UrlAccessToken.class);
      User user = mock(User.class);
      lenient().when(user.getId()).thenReturn(userId);
      lenient().when(token.getUser()).thenReturn(user);
      return token;
    }
  }
}
