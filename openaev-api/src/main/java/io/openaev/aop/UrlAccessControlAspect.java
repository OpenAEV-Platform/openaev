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
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
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

  private final ExpressionParser parser = new SpelExpressionParser();

  @Around("@annotation(urlAccessControl)")
  public Object validateUrlAccess(ProceedingJoinPoint joinPoint, UrlAccessControl urlAccessControl)
      throws Throwable {

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

    // Build a SpEL evaluation context from all method parameters
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    String[] parameterNames = signature.getParameterNames();
    Object[] args = joinPoint.getArgs();

    EvaluationContext context = SimpleEvaluationContext.forReadOnlyDataBinding().build();
    for (int i = 0; i < parameterNames.length; i++) {
      context.setVariable(parameterNames[i], args[i]);
    }

    // Resolve exerciseId via the SpEL expression declared on the annotation
    String exerciseId = evaluateStringExpression(urlAccessControl.exerciseId(), context);

    // Validate token: expiry, revocation, and optional exercise scope
    UrlAccessToken token;
    try {
      token = urlAccessTokenService.validateToken(rawToken, exerciseId);
    } catch (AccessDeniedException e) {
      log.debug("UrlAccessControlAspect: token validation failed - {}", e.getMessage());
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_TOKEN_MESSAGE);
    }

    // Inject the resolved userId into the parameter referenced by the SpEL expression if none was
    // provided
    if (!urlAccessControl.userId().isEmpty()) {
      String paramName = stripSpelPrefix(urlAccessControl.userId());
      for (int i = 0; i < parameterNames.length; i++) {
        if (paramName.equals(parameterNames[i])) {
          Object[] newArgs = Arrays.copyOf(args, args.length);
          newArgs[i] = Optional.of(token.getUser().getId());
          return joinPoint.proceed(newArgs);
        }
      }
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

  /**
   * Evaluates a SpEL expression against the given context and returns its String value, or {@code
   * null} if the expression is empty or does not evaluate to a String.
   */
  private String evaluateStringExpression(String spelExpression, EvaluationContext context) {
    if (spelExpression.isEmpty()) {
      return null;
    }
    Object value = parser.parseExpression(spelExpression).getValue(context);
    return value instanceof String s ? s : null;
  }

  /**
   * Strips the leading {@code #} from a SpEL variable reference (e.g. {@code "#userId"} → {@code
   * "userId"}).
   */
  private static String stripSpelPrefix(String spelExpression) {
    return spelExpression.startsWith("#") ? spelExpression.substring(1) : spelExpression;
  }
}
