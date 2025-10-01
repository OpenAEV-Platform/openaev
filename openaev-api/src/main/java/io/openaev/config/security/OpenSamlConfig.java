package io.openaev.config.security;

import static io.openaev.config.security.SecurityService.OPENAEV_PROVIDER_PATH_PREFIX;
import static io.openaev.database.model.User.ROLE_ADMIN;
import static io.openaev.database.model.User.ROLE_USER;
import static org.springframework.security.saml2.provider.service.authentication.OpenSaml4AuthenticationProvider.createDefaultResponseAuthenticationConverter;

import io.openaev.config.OpenAEVSaml2User;
import io.openaev.database.model.User;
import io.openaev.security.SsoRefererAuthenticationSuccessHandler;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.saml2.core.Saml2Error;
import org.springframework.security.saml2.provider.service.authentication.OpenSaml4AuthenticationProvider;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.security.saml2.provider.service.authentication.Saml2Authentication;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticationException;
import org.springframework.security.saml2.provider.service.metadata.OpenSamlMetadataResolver;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.web.DefaultRelyingPartyRegistrationResolver;
import org.springframework.security.saml2.provider.service.web.Saml2MetadataFilter;
import org.springframework.security.saml2.provider.service.web.authentication.Saml2WebSsoAuthenticationFilter;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class OpenSamlConfig {

  public static final String ROLES_PATH_SUFFIX = ".roles_path";
  public static final String FIRSTNAME_ATTRIBUTE_PATH_SUFFIX = ".firstname_attribute_key";
  public static final String LASTNAME_ATTRIBUTE_PATH_SUFFIX = ".lastname_attribute_key";

  private final Environment env;
  private final SecurityService securityService;

  @Autowired(required = false)
  private RelyingPartyRegistrationRepository relyingPartyRegistrationRepository;

  public void addOpenSamlConfig(@NotNull final HttpSecurity http) throws Exception {
    if (this.relyingPartyRegistrationRepository == null) {
      log.warn("No RelyingPartyRegistrationRepository found, skipping SAML2 configuration.");
      return;
    }

    DefaultRelyingPartyRegistrationResolver relyingPartyRegistrationResolver =
        new DefaultRelyingPartyRegistrationResolver(this.relyingPartyRegistrationRepository);
    Saml2MetadataFilter filter =
        new Saml2MetadataFilter(relyingPartyRegistrationResolver, new OpenSamlMetadataResolver());
    OpenSaml4AuthenticationProvider authenticationProvider = getOpenSaml4AuthenticationProvider();

    http.addFilterBefore(filter, Saml2WebSsoAuthenticationFilter.class)
        .saml2Login(
            saml2Login ->
                saml2Login
                    .authenticationManager(new ProviderManager(authenticationProvider))
                    .successHandler(new SsoRefererAuthenticationSuccessHandler()));
  }

  // -- PRIVATE --

  private OpenSaml4AuthenticationProvider getOpenSaml4AuthenticationProvider() {
    OpenSaml4AuthenticationProvider authenticationProvider = new OpenSaml4AuthenticationProvider();
    authenticationProvider.setResponseAuthenticationConverter(
        responseToken -> {
          Saml2Authentication authentication =
              createDefaultResponseAuthenticationConverter().convert(responseToken);
          assert authentication != null;
          return saml2UserManagement(authentication);
        });
    return authenticationProvider;
  }

  private Saml2Authentication saml2UserManagement(
      @NotNull final Saml2Authentication authentication) {
    Saml2AuthenticatedPrincipal user = (Saml2AuthenticatedPrincipal) authentication.getPrincipal();
    User loginUser = userSaml2Management(user);

    List<SimpleGrantedAuthority> roles = new ArrayList<>();
    roles.add(new SimpleGrantedAuthority(ROLE_USER));
    if (loginUser.isAdmin()) {
      roles.add(new SimpleGrantedAuthority(ROLE_ADMIN));
    }

    return new Saml2Authentication(
        new OpenAEVSaml2User(loginUser, roles), authentication.getSaml2Response(), roles);
  }

  private User userSaml2Management(@NotNull final Saml2AuthenticatedPrincipal user) {
    String emailAttribute = user.getName();
    String registrationId = user.getRelyingPartyRegistrationId();
    List<String> rolesFromUser = extractRolesFromUser(user, registrationId);

    String firstname =
        user.getFirstAttribute(
            env.getProperty(
                OPENAEV_PROVIDER_PATH_PREFIX + registrationId + FIRSTNAME_ATTRIBUTE_PATH_SUFFIX,
                String.class,
                ""));
    String lastname =
        user.getFirstAttribute(
            env.getProperty(
                OPENAEV_PROVIDER_PATH_PREFIX + registrationId + LASTNAME_ATTRIBUTE_PATH_SUFFIX,
                String.class,
                ""));

    try {
      User userLogin =
          securityService.userManagement(
              emailAttribute, registrationId, rolesFromUser, firstname, lastname);

      if (userLogin != null) {
        return userLogin;
      }
    } catch (Exception e) {
      log.error("Failed to manage SAML2 user", e);
    }

    Saml2Error authError = new Saml2Error("invalid_token", "User conversion fail");
    throw new Saml2AuthenticationException(authError);
  }

  private List<String> extractRolesFromUser(
      @NotNull final Saml2AuthenticatedPrincipal user, @NotBlank final String registrationId) {
    List<String> rolesPath = getRoles(registrationId);
    List<String> extractedRoles = new ArrayList<>();

    for (String path : rolesPath) {
      List<String> roles = user.getAttribute(path);
      if (roles != null) {
        extractedRoles.addAll(roles);
      }
    }
    return extractedRoles;
  }

  private List<String> getRoles(@NotBlank final String registrationId) {
    String rolesPathConfig = OPENAEV_PROVIDER_PATH_PREFIX + registrationId + ROLES_PATH_SUFFIX;
    //noinspection unchecked
    return env.getProperty(rolesPathConfig, List.class, new ArrayList<String>());
  }
}
