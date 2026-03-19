package io.openaev.config;

import static io.openaev.config.security.SecurityService.REGISTRATION_ID;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.config.security.OpenSamlConfig;
import io.openaev.config.security.SecurityService;
import io.openaev.database.model.User;
import io.openaev.security.SsoRefererAuthenticationFailureHandler;
import io.openaev.security.SsoRefererAuthenticationSuccessHandler;
import io.openaev.security.TokenAuthenticationFilter;
import io.openaev.service.UserMappingService;
import io.openaev.service.user_events.UserEventService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
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
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@Slf4j
public class AppSecurityConfig {

  private final OpenAEVConfig openAEVConfig;
  private final OpenSamlConfig openSamlConfig;
  private final SecurityService securityService;
  private final UserEventService userEventService;
  private final UserMappingService userMappingService;

  @Resource protected ObjectMapper mapper;

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.addFilterBefore(tokenAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
        .requestCache(Customizer.withDefaults())
        .requestCache(cache -> cache.requestCache(new HttpSessionRequestCache()))
        .csrf(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .securityContext(securityContext -> securityContext.requireExplicitSave(false))
        .authorizeHttpRequests(
            rq ->
                rq.requestMatchers("/api/health")
                    .permitAll()
                    .requestMatchers("/api/comcheck/**")
                    .permitAll()
                    .requestMatchers("/api/player/**")
                    .permitAll()
                    .requestMatchers("/api/settings")
                    .permitAll()
                    .requestMatchers("/api/agent/**")
                    .permitAll()
                    .requestMatchers("/api/implant/**")
                    .permitAll()
                    .requestMatchers("/api/login")
                    .permitAll()
                    .requestMatchers("/api/reset/**")
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
                    .invalidateHttpSession(true)
                    .deleteCookies("JSESSIONID", openAEVConfig.getCookieName())
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
                  .successHandler(new SsoRefererAuthenticationSuccessHandler())
                  .failureHandler(
                      new SsoRefererAuthenticationFailureHandler(this.userEventService)));
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
}
