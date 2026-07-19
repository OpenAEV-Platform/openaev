package io.openaev.config;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

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

  @Bean
  public CookieSerializer cookieSerializer() {
    DefaultCookieSerializer serializer = new DefaultCookieSerializer();
    serializer.setCookieName(SESSION_COOKIE_NAME);
    serializer.setCookiePath("/");
    serializer.setUseHttpOnlyCookie(true);
    serializer.setUseSecureCookie(openAEVConfig.isCookieSecure());
    serializer.setSameSite("Lax");
    if (!openAEVConfig.isSessionCookie()) {
      // Persistent cookie: users stay logged in across browser restarts, capped at the session
      // timeout (the cookie is only written at session creation, so this is an absolute cap).
      serializer.setCookieMaxAge((int) sessionTimeout.toSeconds());
    }
    // Default max-age (-1) = browser-session cookie: dies when the browser closes.
    return serializer;
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
