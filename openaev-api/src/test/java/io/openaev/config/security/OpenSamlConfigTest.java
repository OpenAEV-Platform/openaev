package io.openaev.config.security;

import static io.openaev.config.security.OpenSamlConfig.FIRSTNAME_ATTRIBUTE_PATH_SUFFIX;
import static io.openaev.config.security.OpenSamlConfig.LASTNAME_ATTRIBUTE_PATH_SUFFIX;
import static io.openaev.config.security.SecurityService.OPENAEV_PROVIDER_PATH_PREFIX;
import static io.openaev.database.model.User.ROLE_ADMIN;
import static io.openaev.database.model.User.ROLE_USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import io.openaev.aop.audit_log.AuditLogger;
import io.openaev.config.OpenAEVSaml2User;
import io.openaev.config.SessionManager;
import io.openaev.database.model.User;
import io.openaev.service.UserMappingService;
import io.openaev.service.user_events.UserEventService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.saml2.core.Saml2Error;
import org.springframework.security.saml2.provider.service.authentication.DefaultSaml2AuthenticatedPrincipal;
import org.springframework.security.saml2.provider.service.authentication.Saml2Authentication;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticationException;

@ExtendWith(MockitoExtension.class)
@DisplayName("SAML2 user management")
class OpenSamlConfigTest {

  private static final String REGISTRATION_ID = "okta";
  private static final String EMAIL = "jane@openaev.test";

  @Mock private Environment env;
  @Mock private SecurityService securityService;
  @Mock private UserMappingService userMappingService;
  @Mock private SessionManager sessionManager;
  @Mock private UserEventService userEventService;

  private OpenSamlConfig openSamlConfig;

  @BeforeEach
  void setUp() {
    // Environment.getProperty(key, type, default) never returns null: mirror the default fallback.
    lenient()
        .when(env.getProperty(anyString(), eq(String.class), anyString()))
        .thenAnswer(invocation -> invocation.getArgument(2));
    openSamlConfig =
        new OpenSamlConfig(
            env,
            securityService,
            userMappingService,
            sessionManager,
            userEventService,
            Optional.<AuditLogger>empty());
  }

  private static DefaultSaml2AuthenticatedPrincipal principal() {
    DefaultSaml2AuthenticatedPrincipal principal =
        new DefaultSaml2AuthenticatedPrincipal(
            EMAIL, Map.of("firstname", List.of("Jane"), "lastname", List.of("Doe")));
    principal.setRelyingPartyRegistrationId(REGISTRATION_ID);
    return principal;
  }

  private static Saml2Authentication authentication() {
    return new Saml2Authentication(principal(), "<saml2p:Response/>", List.of());
  }

  private static User user(boolean admin) {
    User user = new User();
    user.setId("user-id");
    user.setEmail(EMAIL);
    user.setAdmin(admin);
    return user;
  }

  @Nested
  @DisplayName("Granted authorities")
  class GrantedAuthorities {

    @Test
    @DisplayName("grants ROLE_USER only to a standard user")
    void should_grant_user_role_only() {
      when(securityService.userManagement(any(), any(), any(), any(), any(), any()))
          .thenReturn(user(false));

      Saml2Authentication result = openSamlConfig.saml2UserManagement(authentication());

      assertThat(result.getAuthorities())
          .extracting(GrantedAuthority::getAuthority)
          .containsExactly(ROLE_USER);
      assertThat(result.getPrincipal()).isInstanceOf(OpenAEVSaml2User.class);
    }

    @Test
    @DisplayName("grants ROLE_ADMIN on top of ROLE_USER to an admin")
    void should_grant_admin_role() {
      when(securityService.userManagement(any(), any(), any(), any(), any(), any()))
          .thenReturn(user(true));

      Saml2Authentication result = openSamlConfig.saml2UserManagement(authentication());

      assertThat(result.getAuthorities())
          .extracting(GrantedAuthority::getAuthority)
          .containsExactlyInAnyOrder(ROLE_USER, ROLE_ADMIN);
    }
  }

  @Nested
  @DisplayName("Failure handling")
  class FailureHandling {

    @Test
    @DisplayName("rejects the login when no user could be resolved")
    void should_reject_when_user_management_returns_null() {
      when(securityService.userManagement(any(), any(), any(), any(), any(), any()))
          .thenReturn(null);

      assertThatThrownBy(() -> openSamlConfig.userSaml2Management(principal()))
          .isInstanceOf(Saml2AuthenticationException.class)
          .extracting(e -> ((Saml2AuthenticationException) e).getSaml2Error())
          .extracting(Saml2Error::getErrorCode)
          .isEqualTo("invalid_token");
    }

    @Test
    @DisplayName("rejects the login when user management blows up")
    void should_reject_when_user_management_throws() {
      when(securityService.userManagement(any(), any(), any(), any(), any(), any()))
          .thenThrow(new IllegalStateException("boom"));

      assertThatThrownBy(() -> openSamlConfig.userSaml2Management(principal()))
          .isInstanceOf(Saml2AuthenticationException.class);
    }
  }

  @Nested
  @DisplayName("Attribute mapping")
  class AttributeMapping {

    @Test
    @DisplayName("reads the name attributes from the registration scoped properties")
    void should_read_names_from_registration_scoped_properties() {
      when(env.getProperty(
              eq(OPENAEV_PROVIDER_PATH_PREFIX + REGISTRATION_ID + FIRSTNAME_ATTRIBUTE_PATH_SUFFIX),
              eq(String.class),
              anyString()))
          .thenReturn("firstname");
      when(env.getProperty(
              eq(OPENAEV_PROVIDER_PATH_PREFIX + REGISTRATION_ID + LASTNAME_ATTRIBUTE_PATH_SUFFIX),
              eq(String.class),
              anyString()))
          .thenReturn("lastname");
      when(securityService.userManagement(
              eq(EMAIL), eq(REGISTRATION_ID), any(), any(), eq("Jane"), eq("Doe")))
          .thenReturn(user(false));

      assertThat(openSamlConfig.userSaml2Management(principal()).getId()).isEqualTo("user-id");
    }
  }
}
