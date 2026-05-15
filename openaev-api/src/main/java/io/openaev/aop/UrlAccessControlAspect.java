package io.openaev.aop;

import static io.openaev.api.url_access_token.UrlAccessTokenApi.URL_ACCESS_COOKIE_NAME;
import static io.openaev.api.url_access_token.UrlAccessTokenService.INVALID_TOKEN_MESSAGE;
import static io.openaev.config.OpenAEVAnonymous.ANONYMOUS;
import static io.openaev.config.SessionHelper.currentUser;

import io.openaev.api.url_access_token.UrlAccessTokenService;
import io.openaev.database.model.UrlAccessToken;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.PreviewFeatureService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

/**
 * AOP aspect enforcing URL access token authentication on methods annotated with {@link
 * UrlAccessControl}.
 *
 * <p>The aspect is a no-op when the {@code URL_ACCESS_TOKEN} feature flag is disabled, allowing the
 * existing {@code userId} query-parameter flow to continue working.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class UrlAccessControlAspect {

  private final UrlAccessTokenService urlAccessTokenService;
  private final PreviewFeatureService previewFeatureService;

  @Around("@annotation(io.openaev.aop.UrlAccessControl)")
  public Object validateUrlAccess(ProceedingJoinPoint joinPoint) throws Throwable {

    // Feature flag off, skip URL access control entirely and proceed with legacy flow
    if (!previewFeatureService.isFeatureEnabled(PreviewFeature.URL_ACCESS_TOKEN)) {
      return joinPoint.proceed();
    }

    // Classical authentication (session/token) already established, no URL token control needed
    if (isClassicallyAuthenticated()) {
      return joinPoint.proceed();
    }

    // Retrieve the current HTTP request
    ServletRequestAttributes requestAttributes =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (requestAttributes == null) {
      throw new IllegalStateException("UrlAccessControlAspect: no HTTP request in current context");
    }
    HttpServletRequest request = requestAttributes.getRequest();

    // Extract the URL access token cookie
    String rawToken = extractCookieValue(request);
    if (rawToken == null) {
      log.debug("UrlAccessControlAspect: missing '{}' cookie", URL_ACCESS_COOKIE_NAME);
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing URL access token cookie");
    }

    // Resolve exerciseId and userId parameter indices from the method signature
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    String[] parameterNames = signature.getParameterNames();
    Object[] args = joinPoint.getArgs();

    String exerciseId = null;
    int userIdParamIndex = -1;

    for (int i = 0; i < parameterNames.length; i++) {
      if ("exerciseId".equals(parameterNames[i]) && args[i] instanceof String s) {
        exerciseId = s;
      }
      if ("userId".equals(parameterNames[i])) {
        userIdParamIndex = i;
      }
    }

    // Validate token — expiry, revocation, and optional exercise scope
    UrlAccessToken token;
    try {
      token = urlAccessTokenService.validateToken(rawToken, exerciseId, null);
    } catch (AccessDeniedException e) {
      log.debug("UrlAccessControlAspect: token validation failed - {}", e.getMessage());
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_TOKEN_MESSAGE);
    }

    // Inject the resolved userId into the method's Optional<String> userId argument
    if (userIdParamIndex >= 0) {
      Object[] newArgs = Arrays.copyOf(args, args.length);
      newArgs[userIdParamIndex] = Optional.of(token.getUser().getId());
      return joinPoint.proceed(newArgs);
    }

    return joinPoint.proceed(args);
  }

  private boolean isClassicallyAuthenticated() {
    try {
      return !ANONYMOUS.equals(currentUser().getId());
    } catch (Exception e) {
      return false;
    }
  }

  private String extractCookieValue(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    return Arrays.stream(cookies)
        .filter(c -> URL_ACCESS_COOKIE_NAME.equals(c.getName()))
        .map(Cookie::getValue)
        .findFirst()
        .orElse(null);
  }
}
