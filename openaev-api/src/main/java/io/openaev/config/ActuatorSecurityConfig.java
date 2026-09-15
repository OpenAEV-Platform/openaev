package io.openaev.config;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.startsWithIgnoreCase;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Dedicated filter chain for the actuator endpoints, which are served on the public API port
 * ({@code management.server.port} equals {@code server.port}).
 *
 * <p>The Prometheus scrape exposes the JVM internals, the connection pool saturation and the
 * platform dependency state, so it is gated behind a static scrape key sent as {@code
 * Authorization: Bearer <key>} — the form Prometheus supports natively via {@code authorization} in
 * a scrape config. The key has no default: when {@code openaev.metrics.key} is unset, every
 * actuator request is rejected, so enabling the endpoint never silently publishes the metrics.
 */
@Configuration
@RequiredArgsConstructor
public class ActuatorSecurityConfig {

  public static final String ACTUATOR_URI = "/actuator/**";

  private static final String BEARER_PREFIX = "Bearer ";

  @Value("${openaev.metrics.key:}")
  private String metricsKey;

  @Bean
  @Order(1)
  public SecurityFilterChain actuatorFilterChain(HttpSecurity http) throws Exception {
    // CSRF is deliberately left enabled: a scrape is a GET, which Spring Security exempts anyway,
    // so disabling it would only widen the surface if a writable endpoint were ever exposed here.
    http.securityMatcher(ACTUATOR_URI)
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .anonymous(AbstractHttpConfigurer::disable)
        // A scrape is stateless: it must not create or join a session.
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .addFilterBefore(
            new ScrapeKeyFilter(this::isAuthorizedScrape),
            UsernamePasswordAuthenticationFilter.class)
        .authorizeHttpRequests(rq -> rq.anyRequest().permitAll());
    return http.build();
  }

  private boolean isAuthorizedScrape(HttpServletRequest request) {
    if (isBlank(metricsKey)) {
      return false;
    }
    String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (authorization == null || !startsWithIgnoreCase(authorization, BEARER_PREFIX)) {
      return false;
    }
    String presentedKey = authorization.substring(BEARER_PREFIX.length());
    return MessageDigest.isEqual(presentedKey.getBytes(UTF_8), metricsKey.getBytes(UTF_8));
  }

  /** Rejects the request before it reaches the actuator endpoints when the scrape key is absent. */
  @RequiredArgsConstructor
  private static class ScrapeKeyFilter extends OncePerRequestFilter {

    private final ScrapeKeyValidator validator;

    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
      if (!validator.isAuthorized(request)) {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        response.sendError(HttpStatus.UNAUTHORIZED.value());
        return;
      }
      filterChain.doFilter(request, response);
    }
  }

  @FunctionalInterface
  private interface ScrapeKeyValidator {
    boolean isAuthorized(HttpServletRequest request);
  }
}
