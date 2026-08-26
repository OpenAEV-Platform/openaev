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
import io.openaev.service.UserMappingService;
import io.openaev.service.user_events.UserEventService;
import io.openaev.utils.fixtures.UserFixture;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.saml2.core.Saml2Error;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticationException;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticationToken;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrations;

@ExtendWith(MockitoExtension.class)
@DisplayName("SAML2 login")
class OpenSamlAuthenticationTest {

  private static final String EMAIL = "jane@openaev.test";
  private static final Map<String, String> ATTRIBUTES =
      Map.of("firstname", "Jane", "lastname", "Doe");

  @Mock private Environment env;
  @Mock private SecurityService securityService;
  @Mock private UserMappingService userMappingService;
  @Mock private SessionManager sessionManager;
  @Mock private UserEventService userEventService;

  private OpenSamlConfig openSamlConfig;

  @BeforeEach
  void setUp() {
    // The provider reads the attribute names to look up from registration scoped properties.
    lenient()
        .when(env.getProperty(anyString(), eq(String.class), anyString()))
        .thenAnswer(invocation -> invocation.getArgument(2));
    lenient()
        .when(
            env.getProperty(
                eq(OPENAEV_PROVIDER_PATH_PREFIX + "openaev" + FIRSTNAME_ATTRIBUTE_PATH_SUFFIX),
                eq(String.class),
                anyString()))
        .thenReturn("firstname");
    lenient()
        .when(
            env.getProperty(
                eq(OPENAEV_PROVIDER_PATH_PREFIX + "openaev" + LASTNAME_ATTRIBUTE_PATH_SUFFIX),
                eq(String.class),
                anyString()))
        .thenReturn("lastname");
    openSamlConfig =
        new OpenSamlConfig(
            env,
            securityService,
            userMappingService,
            sessionManager,
            userEventService,
            Optional.<AuditLogger>empty());
  }

  private static RelyingPartyRegistration registration() {
    return RelyingPartyRegistrations.fromMetadataLocation("classpath:saml/idp-metadata.xml")
        .registrationId("openaev")
        .entityId(SamlResponseFixture.SP_ENTITY_ID)
        .assertionConsumerServiceLocation(SamlResponseFixture.ACS)
        .build();
  }

  private Authentication authenticate(String response) {
    return openSamlConfig
        .getOpenSaml5AuthenticationProvider()
        .authenticate(new Saml2AuthenticationToken(registration(), response));
  }

  @Test
  @DisplayName("authenticates a signed assertion and maps it onto an OpenAEV principal")
  void should_authenticate_signed_assertion() throws Exception {
    when(securityService.userManagement(
            eq(EMAIL), eq("openaev"), any(), any(), eq("Jane"), eq("Doe")))
        .thenReturn(UserFixture.getUser("Jane", "Doe", EMAIL, false));

    Authentication result = authenticate(SamlResponseFixture.signedResponse(EMAIL, ATTRIBUTES));

    assertThat(result.isAuthenticated()).isTrue();
    assertThat(result.getPrincipal()).isInstanceOf(OpenAEVSaml2User.class);
    assertThat(result.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly(ROLE_USER);
  }

  @Test
  @DisplayName("promotes an admin coming from the identity provider")
  void should_authenticate_admin() throws Exception {
    when(securityService.userManagement(
            eq(EMAIL), eq("openaev"), any(), any(), eq("Jane"), eq("Doe")))
        .thenReturn(UserFixture.getUser("Jane", "Doe", EMAIL, true));

    Authentication result = authenticate(SamlResponseFixture.signedResponse(EMAIL, ATTRIBUTES));

    assertThat(result.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactlyInAnyOrder(ROLE_USER, ROLE_ADMIN);
  }

  @Test
  @DisplayName("rejects an assertion whose subject was tampered with after signing")
  void should_reject_tampered_assertion() throws Exception {
    String tampered =
        SamlResponseFixture.signedResponse(EMAIL, ATTRIBUTES).replace(EMAIL, "attacker@evil.test");

    assertThatThrownBy(() -> authenticate(tampered))
        .isInstanceOf(Saml2AuthenticationException.class)
        .extracting(e -> ((Saml2AuthenticationException) e).getSaml2Error())
        .extracting(Saml2Error::getErrorCode)
        .isEqualTo("invalid_signature");
  }

  @Test
  @DisplayName("rejects an assertion issued for another audience")
  void should_reject_unknown_audience() throws Exception {
    RelyingPartyRegistration otherAudience =
        RelyingPartyRegistrations.fromMetadataLocation("classpath:saml/idp-metadata.xml")
            .registrationId("openaev")
            .entityId("https://someone.else/sp")
            .assertionConsumerServiceLocation(SamlResponseFixture.ACS)
            .build();

    Saml2AuthenticationToken token =
        new Saml2AuthenticationToken(
            otherAudience, SamlResponseFixture.signedResponse(EMAIL, ATTRIBUTES));

    assertThatThrownBy(
            () -> openSamlConfig.getOpenSaml5AuthenticationProvider().authenticate(token))
        .isInstanceOf(Saml2AuthenticationException.class)
        .extracting(e -> ((Saml2AuthenticationException) e).getSaml2Error())
        .extracting(Saml2Error::getErrorCode)
        .isEqualTo("invalid_assertion");
  }

  @Test
  void should_reject_when_user_management_returns_no_user() throws Exception {
    when(securityService.userManagement(any(), any(), any(), any(), any(), any())).thenReturn(null);

    assertThatThrownBy(() -> authenticate(SamlResponseFixture.signedResponse(EMAIL, ATTRIBUTES)))
        .isInstanceOf(Saml2AuthenticationException.class)
        .extracting(e -> ((Saml2AuthenticationException) e).getSaml2Error())
        .extracting(Saml2Error::getErrorCode)
        .isEqualTo("invalid_token");
  }

  @Test
  void should_reject_when_user_management_throws() throws Exception {
    when(securityService.userManagement(any(), any(), any(), any(), any(), any()))
        .thenThrow(new IllegalStateException("boom"));

    assertThatThrownBy(() -> authenticate(SamlResponseFixture.signedResponse(EMAIL, ATTRIBUTES)))
        .isInstanceOf(Saml2AuthenticationException.class)
        .extracting(e -> ((Saml2AuthenticationException) e).getSaml2Error())
        .extracting(Saml2Error::getErrorCode)
        .isEqualTo("invalid_token");
  }
}
