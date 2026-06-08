package io.openaev.config.security;

import static io.openaev.database.model.User.ROLE_ADMIN;
import static io.openaev.database.model.User.ROLE_USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.openaev.database.model.User;
import io.openaev.service.UserMappingService;
import io.openaev.service.user_events.UserEventService;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.security.saml2.provider.service.authentication.Saml2Authentication;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticationException;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.web.authentication.Saml2WebSsoAuthenticationFilter;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("OpenSamlConfig")
class OpenSamlConfigTest {

  @Nested
  @DisplayName("addOpenSamlConfig")
  class AddOpenSamlConfig {

    @Test
    void given_nullRepository_should_returnWithoutThrowing() {
      // Arrange
      OpenSamlConfig config = buildConfig();
      HttpSecurity httpSecurity = mock(HttpSecurity.class);

      // Act / Assert
      assertDoesNotThrow(() -> config.addOpenSamlConfig(httpSecurity));
    }

    @Test
    void given_repositoryPresent_should_configureSamlWithoutThrowing() throws Exception {
      // Arrange
      OpenSamlConfig config = buildConfig();
      RelyingPartyRegistrationRepository repository =
          mock(RelyingPartyRegistrationRepository.class);
      setRepositoryField(config, repository);

      HttpSecurity httpSecurity = mock(HttpSecurity.class);
      doReturn(httpSecurity)
          .when(httpSecurity)
          .addFilterBefore(any(), eq(Saml2WebSsoAuthenticationFilter.class));
      doReturn(httpSecurity).when(httpSecurity).saml2Login(any());

      // Act / Assert
      assertDoesNotThrow(() -> config.addOpenSamlConfig(httpSecurity));
    }
  }

  @Nested
  @DisplayName("saml2UserManagement")
  class Saml2UserManagement {

    @Test
    void given_userManagementReturnsNull_should_throwSaml2AuthenticationException() {
      // Arrange
      Environment env = mock(Environment.class);
      SecurityService securityService = mock(SecurityService.class);
      UserMappingService userMappingService = mock(UserMappingService.class);
      UserEventService userEventService = mock(UserEventService.class);

      OpenSamlConfig config =
          new OpenSamlConfig(
              env, securityService, userMappingService, userEventService, Optional.empty());

      when(userMappingService.extractRolesFromUser(any(), anyString()))
          .thenReturn(java.util.List.of());
      when(userMappingService.extractGroupsFromUser(any(), anyString()))
          .thenReturn(java.util.List.of());
      when(env.getProperty(anyString(), eq(String.class), anyString())).thenReturn("");
      when(securityService.userManagement(
              anyString(), anyString(), anyList(), anyList(), any(), any()))
          .thenReturn(null);

      Saml2Authentication authentication = buildSamlAuthentication("user@openaev.io");

      // Act / Assert
      assertThrows(
          Saml2AuthenticationException.class,
          () -> invokeSaml2UserManagement(config, authentication));
    }

    @Test
    void given_userManagementReturnsAdmin_should_returnSamlAuthWithUserAndAdminRoles() {
      // Arrange
      Environment env = mock(Environment.class);
      SecurityService securityService = mock(SecurityService.class);
      UserMappingService userMappingService = mock(UserMappingService.class);
      UserEventService userEventService = mock(UserEventService.class);

      OpenSamlConfig config =
          new OpenSamlConfig(
              env, securityService, userMappingService, userEventService, Optional.empty());

      when(userMappingService.extractRolesFromUser(any(), anyString()))
          .thenReturn(java.util.List.of());
      when(userMappingService.extractGroupsFromUser(any(), anyString()))
          .thenReturn(java.util.List.of());
      when(env.getProperty(anyString(), eq(String.class), anyString())).thenReturn("");

      User admin = new User();
      admin.setEmail("admin@openaev.io");
      admin.setFirstname("Admin");
      admin.setLastname("User");
      admin.setAdmin(true);

      when(securityService.userManagement(
              anyString(), anyString(), anyList(), anyList(), any(), any()))
          .thenReturn(admin);

      Saml2Authentication authentication = buildSamlAuthentication("admin@openaev.io");

      // Act
      Saml2Authentication result = invokeSaml2UserManagement(config, authentication);

      // Assert
      Set<String> authorities =
          result.getAuthorities().stream()
              .map(org.springframework.security.core.GrantedAuthority::getAuthority)
              .collect(java.util.stream.Collectors.toSet());
      assertThat(authorities).contains(ROLE_USER, ROLE_ADMIN);
    }
  }

  private OpenSamlConfig buildConfig() {
    Environment env = mock(Environment.class);
    SecurityService securityService = mock(SecurityService.class);
    UserMappingService userMappingService = mock(UserMappingService.class);
    UserEventService userEventService = mock(UserEventService.class);
    return new OpenSamlConfig(
        env, securityService, userMappingService, userEventService, Optional.empty());
  }

  private void setRepositoryField(Object target, Object value) {
    try {
      Field field = target.getClass().getDeclaredField("relyingPartyRegistrationRepository");
      field.setAccessible(true);
      field.set(target, value);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private Saml2Authentication buildSamlAuthentication(String email) {
    Saml2AuthenticatedPrincipal principal = mock(Saml2AuthenticatedPrincipal.class);
    when(principal.getName()).thenReturn(email);
    when(principal.getRelyingPartyRegistrationId()).thenReturn("oidc");
    when(principal.getFirstAttribute(anyString())).thenReturn(null);
    return new Saml2Authentication(principal, "saml-response", java.util.List.of());
  }

  private Saml2Authentication invokeSaml2UserManagement(
      OpenSamlConfig config, Saml2Authentication authentication) {
    try {
      Method method =
          OpenSamlConfig.class.getDeclaredMethod("saml2UserManagement", Saml2Authentication.class);
      method.setAccessible(true);
      return (Saml2Authentication) method.invoke(config, authentication);
    } catch (java.lang.reflect.InvocationTargetException e) {
      if (e.getCause() instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      throw new RuntimeException(e.getCause());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
