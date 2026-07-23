package io.openaev.config;

import static io.openaev.config.security.SecurityService.REGISTRATION_ID;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.startsWithIgnoreCase;
import static org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.aop.audit_log.AuditEventScope;
import io.openaev.aop.audit_log.AuditLogger;
import io.openaev.config.security.OpenSamlConfig;
import io.openaev.config.security.SecurityService;
import io.openaev.database.model.EventStatus;
import io.openaev.database.model.User;
import io.openaev.security.SsoRefererAuthenticationFailureHandler;
import io.openaev.security.SsoRefererAuthenticationSuccessHandler;
import io.openaev.security.TokenAuthenticationFilter;
import io.openaev.service.UserMappingService;
import io.openaev.service.user_events.UserEventService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.core.context.DeferredSecurityContext;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.HttpRequestResponseHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@Slf4j
public class AppSecurityConfig {

  private static final String TENANT_AGENT_URI = "/api/tenants/*/agent/**";
  private static final String TENANT_IMPLANT_URI = "/api/tenants/*/implant/**";
  private static final String TENANT_PLAYER_URI = "/api/tenants/*/player/**";
  private static final String BEARER_PREFIX = "Bearer ";

  private final OpenAEVConfig openAEVConfig;
  private final OpenSamlConfig openSamlConfig;
  private final SecurityService securityService;
  private final UserEventService userEventService;
  private final UserMappingService userMappingService;
  private final SessionManager sessionManager;

  private final Optional<AuditLogger> auditLogger;

  @Resource protected ObjectMapper mapper;

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.addFilterBefore(tokenAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
        .requestCache(Customizer.withDefaults())
        .requestCache(cache -> cache.requestCache(new HttpSessionRequestCache()))
        .csrf(
            csrf ->
                csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                    .ignoringRequestMatchers("/api/health", "/api/login", "/actuator/**")
                    .ignoringRequestMatchers(bearerWithoutCookiesMatcher()))
        .formLogin(AbstractHttpConfigurer::disable)
        // Spring Security defaults X-Frame-Options to DENY, which blocks the reporting
        // live-preview iframe (same-origin embed of the chrome-less render route).
        // SAMEORIGIN keeps cross-origin clickjacking protection while allowing our own frames.
        .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin))
        .securityContext(
            securityContext ->
                securityContext
                    .requireExplicitSave(false)
                    .securityContextRepository(bearerAwareSecurityContextRepository()))
        .authorizeHttpRequests(
            rq ->
                rq.requestMatchers("/api/health")
                    .permitAll()
                    .requestMatchers("/api/comcheck/**")
                    .permitAll()
                    // TODO multi-tenancy to delete after the multi tenancy upgrade
                    .requestMatchers("/api/player/**")
                    .permitAll()
                    .requestMatchers(TENANT_PLAYER_URI)
                    .permitAll()
                    .requestMatchers("/api/settings/public")
                    .permitAll()
                    // TODO multi-tenancy to delete after the multi tenancy upgrade
                    .requestMatchers("/api/agent/**")
                    .permitAll()
                    .requestMatchers(TENANT_AGENT_URI)
                    .permitAll()
                    // TODO multi-tenancy to delete after the multi tenancy upgrade
                    .requestMatchers("/api/implant/**")
                    .permitAll()
                    .requestMatchers(TENANT_IMPLANT_URI)
                    .permitAll()
                    .requestMatchers("/api/login")
                    .permitAll()
                    .requestMatchers("/api/url/access/**")
                    .permitAll()
                    .requestMatchers("/api/tenants/*/url/access/**")
                    .permitAll()
                    .requestMatchers("/api/reset/**")
                    .permitAll()
                    .requestMatchers("/xtm/auth/jwks")
                    .permitAll()
                    .requestMatchers("/api/**")
                    .authenticated()
                    .requestMatchers("/actuator/**")
                    .permitAll()
                    .anyRequest()
                    .permitAll())
        .logout(
            logout ->
                logout
                    // Audit Log: audit handler fires first, then Spring Security's built-in
                    // SecurityContextLogoutHandler
                    // invalidates the session and clears cookies
                    .addLogoutHandler(
                        (request, response, authentication) -> {
                          // Mark session as explicitly logged out BEFORE invalidation,
                          // so sessionDestroyed does not emit a spurious session_expired event.
                          HttpSession session = request.getSession(false);
                          if (session != null) {
                            session.setAttribute(SessionManager.EXPLICIT_LOGOUT, Boolean.TRUE);
                          }

                          auditLogger.ifPresent(
                              logger -> {
                                ThreadPoolTaskLoggerConfig.ThreadRequestContextHolder
                                        .RequestContextData
                                    rcd = null;
                                try {
                                  rcd =
                                      ThreadPoolTaskLoggerConfig.buildThreadRequestContextHolder(
                                          request, authentication);
                                } catch (Exception e) {
                                  // Never block the logout flow
                                  log.error(
                                      "Failed to prepare request context on the logout callback handler: {}",
                                      e.getMessage(),
                                      e);
                                }

                                logger.logAuthEventWithRequestContext(
                                    rcd, AuditEventScope.LOGOUT, EventStatus.SUCCESS, null, null);
                              });
                        })
                    .invalidateHttpSession(true)
                    .deleteCookies(
                        "JSESSIONID",
                        SpringSessionConfig.SESSION_COOKIE_NAME,
                        openAEVConfig.getCookieName())
                    .logoutSuccessUrl(
                        openAEVConfig.getFrontendUrl() + openAEVConfig.getLogoutSuccessUrl()));

    if (openAEVConfig.isAuthOpenidEnable()) {
      http.oauth2Login(
          login ->
              login
                  .authorizationEndpoint(
                      auth ->
                          auth.authorizationRequestResolver(
                              authorizationRequestResolver(
                                  http.getSharedObject(ClientRegistrationRepository.class))))
                  .successHandler(
                      new SsoRefererAuthenticationSuccessHandler(
                          this.auditLogger.orElse(null), this.sessionManager))
                  .failureHandler(
                      new SsoRefererAuthenticationFailureHandler(
                          this.userEventService, this.auditLogger.orElse(null))));
    }

    if (openAEVConfig.isAuthSaml2Enable()) {
      this.openSamlConfig.addOpenSamlConfig(http);
    }

    // Rewrite 403 code to 401
    http.exceptionHandling(
        exceptionHandling ->
            exceptionHandling.authenticationEntryPoint(
                (request, response, authException) ->
                    response.setStatus(HttpStatus.UNAUTHORIZED.value())));

    return http.build();
  }

  @Bean
  public TokenAuthenticationFilter tokenAuthenticationFilter() {
    return new TokenAuthenticationFilter();
  }

  public User userOauth2Management(ClientRegistration clientRegistration, OAuth2User user) {
    String emailAttribute = user.getAttribute("email");
    String registrationId = clientRegistration.getRegistrationId();
    List<String> rolesFromUser = userMappingService.extractRolesFromUser(user, registrationId);
    List<String> groupsFromUser = userMappingService.extractGroupsFromUser(user, registrationId);
    if (isBlank(emailAttribute)) {
      OAuth2Error authError =
          new OAuth2Error(
              "invalid_configuration",
              "You probably need a public email in your " + registrationId + " account",
              "");
      throw new OAuth2AuthenticationException(authError);
    }
    User userLogin =
        this.securityService.userManagement(
            emailAttribute,
            registrationId,
            rolesFromUser,
            groupsFromUser,
            user.getAttribute("given_name"),
            user.getAttribute("family_name"));

    if (userLogin != null) {
      return userLogin;
    }

    OAuth2Error authError = new OAuth2Error("invalid_token", "User conversion fail", "");
    throw new OAuth2AuthenticationException(authError);
  }

  public OidcUser oidcUserManagement(ClientRegistration clientRegistration, OAuth2User user) {
    User loginUser = userOauth2Management(clientRegistration, user);
    return new OpenAEVOidcUser(loginUser);
  }

  public OAuth2User oAuth2UserManagement(ClientRegistration clientRegistration, OAuth2User user) {
    User loginUser = userOauth2Management(clientRegistration, user);
    return new OpenAEVOAuth2User(loginUser);
  }

  @Bean
  public OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService() {
    OidcUserService delegate = new OidcUserService();
    return request ->
        oidcUserManagement(request.getClientRegistration(), delegate.loadUser(request));
  }

  @Bean
  public OAuth2UserService<OAuth2UserRequest, OAuth2User> oauth2UserService() {
    DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    return request ->
        oAuth2UserManagement(request.getClientRegistration(), delegate.loadUser(request));
  }

  @Bean
  @ConditionalOnProperty(name = "openaev.auth-openid-enable", havingValue = "true")
  public OAuth2AuthorizationRequestResolver authorizationRequestResolver(
      ClientRegistrationRepository clientRegistrationRepository) {

    DefaultOAuth2AuthorizationRequestResolver defaultResolver =
        new DefaultOAuth2AuthorizationRequestResolver(
            clientRegistrationRepository, DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);

    return new OAuth2AuthorizationRequestResolver() {

      @Override
      public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return customize(defaultResolver.resolve(request));
      }

      @Override
      public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String registrationId) {
        return customize(defaultResolver.resolve(request, registrationId));
      }

      private OAuth2AuthorizationRequest customize(
          OAuth2AuthorizationRequest authorizationRequest) {

        if (authorizationRequest == null) {
          return null;
        }

        String registrationId = (String) authorizationRequest.getAttributes().get(REGISTRATION_ID);

        String audience = securityService.getAudience(registrationId);

        if (isBlank(audience)) {
          return authorizationRequest;
        }

        return OAuth2AuthorizationRequest.from(authorizationRequest)
            .additionalParameters(params -> params.put("audience", audience))
            .build();
      }
    };
  }

  /**
   * Skip CSRF only for a bearer request that sends no cookies.
   *
   * <p>A pure API client authenticates statelessly with a bearer token and sends no cookies.
   *
   * <p>Bearer auth is stateless (bearerAwareSecurityContextRepository) so no session cookie is set.
   *
   * <p>So a standard client keeps passing on every call, not just the first (the #6343 regression).
   *
   * <p>A request that also sends cookies keeps full CSRF protection (no bearer bypass).
   */
  private RequestMatcher bearerWithoutCookiesMatcher() {
    return request -> {
      boolean hasCookies = request.getCookies() != null && request.getCookies().length > 0;
      return hasBearerToken(request) && !hasCookies;
    };
  }

  /**
   * Whether the request carries a bearer token. Single-sourced so the CSRF-skip rule and the
   * stateless-context rule can never disagree on what counts as a bearer request.
   */
  private static boolean hasBearerToken(HttpServletRequest request) {
    return startsWithIgnoreCase(request.getHeader(HttpHeaders.AUTHORIZATION), BEARER_PREFIX);
  }

  /**
   * Persists the security context in the HTTP session for browser / cookie auth.
   *
   * <p>Authorization-header (bearer) requests stay stateless - no session is read or created.
   *
   * <p>So a pure API client never receives a JSESSIONID to replay (#6343); SSO sessions are intact.
   */
  private SecurityContextRepository bearerAwareSecurityContextRepository() {
    return new BearerAwareSecurityContextRepository();
  }

  /** See {@link #bearerAwareSecurityContextRepository()}. */
  private static final class BearerAwareSecurityContextRepository
      implements SecurityContextRepository {

    private final HttpSessionSecurityContextRepository sessionRepository =
        new HttpSessionSecurityContextRepository();
    private final RequestAttributeSecurityContextRepository statelessRepository =
        new RequestAttributeSecurityContextRepository();

    private SecurityContextRepository delegate(HttpServletRequest request) {
      return hasBearerToken(request) ? statelessRepository : sessionRepository;
    }

    @Override
    @SuppressWarnings("deprecation")
    public SecurityContext loadContext(HttpRequestResponseHolder requestResponseHolder) {
      return delegate(requestResponseHolder.getRequest()).loadContext(requestResponseHolder);
    }

    @Override
    public DeferredSecurityContext loadDeferredContext(HttpServletRequest request) {
      return delegate(request).loadDeferredContext(request);
    }

    @Override
    public boolean containsContext(HttpServletRequest request) {
      return delegate(request).containsContext(request);
    }

    @Override
    public void saveContext(
        SecurityContext context, HttpServletRequest request, HttpServletResponse response) {
      delegate(request).saveContext(context, request, response);
    }
  }
}
