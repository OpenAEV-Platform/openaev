package io.openaev.config;

import jakarta.servlet.DispatcherType;
import java.time.Duration;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.session.web.http.SessionRepositoryFilter;

/**
 * Spring Session (JDBC) configuration: sessions are persisted in PostgreSQL so users stay logged in
 * across platform restarts.
 *
 * <p>Session semantics mirror OpenCTI's session management: a rolling idle timeout (every request
 * slides the expiration, {@code server.servlet.session.timeout}), a dedicated branded cookie, and
 * an optional browser-session cookie mode ({@code openaev.session-cookie}).
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class SpringSessionConfig {

  /** Name of the persistent session cookie (replaces the container JSESSIONID). */
  public static final String SESSION_COOKIE_NAME = "openaev_session";

  private final OpenAEVConfig openAEVConfig;

  @Value("${server.servlet.session.timeout:1440m}")
  private Duration sessionTimeout;

  /**
   * SameSite attribute of the session cookie.
   *
   * <p>Left blank by default so the attribute is OMITTED, which is what the platform did before
   * sessions moved to Spring Session (the servlet container never set SameSite). Omitting it lets
   * the browser apply its default policy, which still delivers the cookie on the cross-site SSO
   * callback (SAML ACS POST and OIDC {@code form_post}). An explicit {@code Lax} would drop the
   * cookie on that POST and break SSO.
   *
   * <p>Production SSO deployments behind HTTPS should set this to {@code None} (which forces the
   * {@code Secure} attribute) for a robust, spec-compliant cross-site session cookie. Accepted
   * values: {@code None}, {@code Lax}, {@code Strict}, or blank to omit.
   */
  @Value("${openbas.session-cookie-same-site:${openaev.session-cookie-same-site:}}")
  private String sessionCookieSameSite;

  @Bean
  public CookieSerializer cookieSerializer() {
    DefaultCookieSerializer serializer = new DefaultCookieSerializer();
    serializer.setCookieName(SESSION_COOKIE_NAME);
    serializer.setCookiePath("/");
    serializer.setUseHttpOnlyCookie(true);

    // SameSite handling is critical for SSO: the SAML ACS response and OIDC form_post callback
    // arrive as a cross-site POST, and an explicit SameSite=Lax would drop the session cookie on
    // that POST, losing the stored authorization request and failing the login (redirect to
    // /login?error). We therefore honor the configured value and, by default, OMIT the attribute
    // (restoring the pre-Spring-Session behavior where the cookie is still delivered on the SSO
    // callback). SameSite=None additionally requires Secure to be accepted by browsers.
    String sameSite = normalizeSameSite(sessionCookieSameSite);
    serializer.setSameSite(sameSite);
    boolean secure = openAEVConfig.isCookieSecure() || "None".equals(sameSite);
    serializer.setUseSecureCookie(secure);

    if (!openAEVConfig.isSessionCookie()) {
      // Persistent cookie: users stay logged in across browser restarts. The Max-Age is NOT an
      // absolute cap: the RollingSessionCookieFilter re-issues the cookie on every request, so
      // browser-side expiration slides with activity exactly like the server-side idle timeout
      // (OpenCTI's express-session `rolling: true` semantics).
      serializer.setCookieMaxAge((int) sessionTimeout.toSeconds());
    }
    // Default max-age (-1) = browser-session cookie: dies when the browser closes.
    return serializer;
  }

  /**
   * Slides the session cookie's {@code Max-Age} on every request carrying a valid session, so an
   * active user is never logged out by the browser dropping the cookie while the server-side
   * session (rolling idle timeout) is still alive.
   *
   * <p>Ordered right after Spring Session's {@link SessionRepositoryFilter} so the filter sees the
   * wrapped request (Spring Session ids, store-backed {@code getSession}).
   */
  @Bean
  public FilterRegistrationBean<RollingSessionCookieFilter> rollingSessionCookieFilter(
      CookieSerializer cookieSerializer) {
    FilterRegistrationBean<RollingSessionCookieFilter> registration =
        new FilterRegistrationBean<>(
            new RollingSessionCookieFilter(cookieSerializer, !openAEVConfig.isSessionCookie()));
    registration.addUrlPatterns("/*");
    registration.setDispatcherTypes(DispatcherType.REQUEST);
    registration.setOrder(SessionRepositoryFilter.DEFAULT_ORDER + 1);
    return registration;
  }

  /**
   * Maps the configured value to a valid SameSite token, or {@code null} to omit the attribute
   * entirely. Unknown / blank values omit the attribute so a misconfiguration can never silently
   * break SSO with an over-restrictive policy.
   */
  private static String normalizeSameSite(String configured) {
    if (configured == null) {
      return null;
    }
    return switch (configured.trim().toLowerCase(Locale.ROOT)) {
      case "none" -> "None";
      case "lax" -> "Lax";
      case "strict" -> "Strict";
      default -> null;
    };
  }

  /**
   * Session attribute (de)serialization used by Spring Session JDBC.
   *
   * <p>Serialization is the standard JDK strategy; deserialization is made fault-tolerant: if a
   * stored attribute cannot be deserialized (typically after an upgrade changed a class shape), the
   * attribute is dropped instead of breaking every request carrying the old cookie. The worst case
   * for the user is a re-login, never an error page.
   *
   * <p>Deserialization MUST resolve classes through the application classloader (this class's
   * loader): with Spring Boot devtools the app runs in the RestartClassLoader, and the default
   * deserializer would load principals from the base classloader instead, making every cast on a
   * session-restored principal fail with a cross-classloader ClassCastException.
   */
  @Bean("springSessionConversionService")
  public ConversionService springSessionConversionService() {
    GenericConversionService conversionService = new GenericConversionService();
    SerializingConverter serializer = new SerializingConverter();
    DeserializingConverter deserializer = new DeserializingConverter(getClass().getClassLoader());
    conversionService.addConverter(Object.class, byte[].class, serializer::convert);
    conversionService.addConverter(
        byte[].class,
        Object.class,
        bytes -> {
          try {
            return deserializer.convert(bytes);
          } catch (Exception e) {
            log.warn(
                "Dropping non-deserializable session attribute (stale session after upgrade?): {}",
                e.getMessage());
            return null;
          }
        });
    return conversionService;
  }
}
