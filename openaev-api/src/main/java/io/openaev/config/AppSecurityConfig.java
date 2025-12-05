package io.openaev.config;

import static io.openaev.config.security.SecurityService.OPENAEV_PROVIDER_PATH_PREFIX;
import static org.apache.commons.lang3.StringUtils.isBlank;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import io.openaev.config.security.OpenSamlConfig;
import io.openaev.config.security.SecurityService;
import io.openaev.database.model.User;
import io.openaev.security.SsoRefererAuthenticationFailureHandler;
import io.openaev.security.SsoRefererAuthenticationSuccessHandler;
import io.openaev.security.TokenAuthenticationFilter;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
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

  private final Environment env;
  private final OpenAEVConfig openAEVConfig;
  private final OpenSamlConfig openSamlConfig;
  private final SecurityService securityService;

  @Resource protected ObjectMapper mapper;

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.addFilterBefore(tokenAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
        .requestCache(Customizer.withDefaults())
        /**/ .requestCache((cache) -> cache.requestCache(new HttpSessionRequestCache()))
        .csrf(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .securityContext((securityContext) -> securityContext.requireExplicitSave(false))
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
                  .successHandler(new SsoRefererAuthenticationSuccessHandler())
                  .failureHandler(new SsoRefererAuthenticationFailureHandler()));
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

  private List<String> extractRolesFromToken(OAuth2AccessToken accessToken, String registrationId) {
    ObjectReader listReader = mapper.readerFor(new TypeReference<List<String>>() {});
    if (accessToken != null) {
      String rolesPathConfig = OPENAEV_PROVIDER_PATH_PREFIX + registrationId + ".roles_path";
      //noinspection unchecked
      List<String> rolesPath =
          env.getProperty(rolesPathConfig, List.class, new ArrayList<String>());
      try {
        String[] chunks = accessToken.getTokenValue().split("\\.");
        Base64.Decoder decoder = Base64.getUrlDecoder();
        String payload = new String(decoder.decode(chunks[1]));
        JsonNode jsonNode = mapper.readTree(payload);
        return rolesPath.stream()
            .map(path -> "/" + path.replaceAll("\\.", "/"))
            .flatMap(
                path -> {
                  JsonNode arrayRoles = jsonNode.at(path);
                  try {
                    List<String> roles = listReader.readValue(arrayRoles);
                    return roles.stream();
                  } catch (IOException e) {
                    return Stream.empty();
                  }
                })
            .toList();
      } catch (Exception e) {
        log.error(e.getMessage(), e);
      }
    }
    return new ArrayList<>();
  }

  public User userOauth2Management(
      OAuth2AccessToken accessToken, ClientRegistration clientRegistration, OAuth2User user) {
    String emailAttribute = user.getAttribute("email");
    String registrationId = clientRegistration.getRegistrationId();
    List<String> rolesFromToken = extractRolesFromToken(accessToken, registrationId);
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
            rolesFromToken,
            user.getAttribute("given_name"),
            user.getAttribute("family_name"));

    if (userLogin != null) {
      return userLogin;
    }

    OAuth2Error authError = new OAuth2Error("invalid_token", "User conversion fail", "");
    throw new OAuth2AuthenticationException(authError);
  }

  public OidcUser oidcUserManagement(
      OAuth2AccessToken accessToken, ClientRegistration clientRegistration, OAuth2User user) {
    User loginUser = userOauth2Management(accessToken, clientRegistration, user);
    return new OpenAEVOidcUser(loginUser);
  }

  public OAuth2User oAuth2UserManagement(
      OAuth2AccessToken accessToken, ClientRegistration clientRegistration, OAuth2User user) {
    User loginUser = userOauth2Management(accessToken, clientRegistration, user);
    return new OpenAEVOAuth2User(loginUser);
  }

  @Bean
  public OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService() {
    OidcUserService delegate = new OidcUserService();
    return request ->
        oidcUserManagement(
            request.getAccessToken(), request.getClientRegistration(), delegate.loadUser(request));
  }

  @Bean
  public OAuth2UserService<OAuth2UserRequest, OAuth2User> oauth2UserService() {
    DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    return request ->
        oAuth2UserManagement(
            request.getAccessToken(), request.getClientRegistration(), delegate.loadUser(request));
  }
}
