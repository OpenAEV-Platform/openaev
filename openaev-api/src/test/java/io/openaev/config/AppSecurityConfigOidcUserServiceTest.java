package io.openaev.config;

import static io.openaev.utils.fixtures.OidcUserRequestFixture.EMAIL;
import static io.openaev.utils.fixtures.OidcUserRequestFixture.REGISTRATION_ID;
import static io.openaev.utils.fixtures.OidcUserRequestFixture.getOidcUserRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

import io.openaev.config.security.OpenSamlConfig;
import io.openaev.config.security.SecurityService;
import io.openaev.ratelimit.config.RateLimitConfig;
import io.openaev.ratelimit.service.RateLimitService;
import io.openaev.service.UserMappingService;
import io.openaev.service.user_events.UserEventService;
import io.openaev.utils.RequestUtils;
import io.openaev.utils.fixtures.UserFixture;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

@ExtendWith(MockitoExtension.class)
@DisplayName("OIDC login user info retrieval")
class AppSecurityConfigOidcUserServiceTest {

  @Mock private OpenAEVConfig openAEVConfig;
  @Mock private OpenSamlConfig openSamlConfig;
  @Mock private SecurityService securityService;
  @Mock private UserEventService userEventService;
  @Mock private UserMappingService userMappingService;
  @Mock private RateLimitService rateLimitService;
  @Mock private RateLimitConfig rateLimitConfig;
  @Mock private SessionManager sessionManager;
  @Mock private RequestUtils requestUtils;

  private OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService;

  @BeforeEach
  void setUp() {
    lenient()
        .when(
            securityService.userManagement(
                eq(EMAIL), eq(REGISTRATION_ID), anyList(), anyList(), any(), any()))
        .thenReturn(UserFixture.getUser("Jane", "Doe", EMAIL, false));
    oidcUserService =
        new AppSecurityConfig(
                openAEVConfig,
                openSamlConfig,
                securityService,
                userEventService,
                userMappingService,
                rateLimitService,
                rateLimitConfig,
                sessionManager,
                requestUtils,
                Optional.empty())
            .oidcUserService();
  }

  @Nested
  @DisplayName("UserInfo is skipped")
  class UserInfoSkipped {

    @Test
    @DisplayName("given only the openid scope, should log in from the ID token")
    void given_openidScopeOnly_should_logInFromIdToken() {
      // Arrange
      OidcUserRequest request = getOidcUserRequest(Set.of("openid"));

      // Act
      OidcUser user = oidcUserService.loadUser(request);

      // Assert
      assertThat(user.getEmail()).isEqualTo(EMAIL);
    }

    @Test
    @DisplayName("given no UserInfo endpoint, should log in from the ID token")
    void given_noUserInfoEndpoint_should_logInFromIdToken() {
      // Arrange
      OidcUserRequest request = getOidcUserRequest(null, Set.of("openid", "profile"));

      // Act
      OidcUser user = oidcUserService.loadUser(request);

      // Assert
      assertThat(user.getEmail()).isEqualTo(EMAIL);
    }
  }

  @Nested
  @DisplayName("UserInfo is called")
  class UserInfoCalled {

    @Test
    @DisplayName("given the profile scope, should call UserInfo")
    void given_profileScope_should_callUserInfo() {
      // Arrange
      OidcUserRequest request = getOidcUserRequest(Set.of("openid", "profile"));

      // Act & Assert
      assertThatThrownBy(() -> oidcUserService.loadUser(request))
          .isInstanceOf(OAuth2AuthenticationException.class)
          .hasMessageContaining("invalid_user_info_response");
    }
  }
}
