package io.openaev.utils.fixtures;

import java.time.Instant;
import java.util.Set;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;

public class OidcUserRequestFixture {

  public static final String REGISTRATION_ID = "idp";
  public static final String EMAIL = "jane@openaev.test";
  // Nothing listens there: any call fails with invalid_user_info_response.
  public static final String UNREACHABLE_USER_INFO_URI = "http://127.0.0.1:1/userinfo";

  public static OidcUserRequest getOidcUserRequest(Set<String> grantedScopes) {
    return getOidcUserRequest(UNREACHABLE_USER_INFO_URI, grantedScopes);
  }

  public static OidcUserRequest getOidcUserRequest(String userInfoUri, Set<String> grantedScopes) {
    ClientRegistration registration =
        ClientRegistration.withRegistrationId(REGISTRATION_ID)
            .clientId("client")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .scope("openid")
            .authorizationUri("http://127.0.0.1:1/authorize")
            .tokenUri("http://127.0.0.1:1/token")
            .jwkSetUri("http://127.0.0.1:1/jwks")
            .userInfoUri(userInfoUri)
            .userNameAttributeName("sub")
            .build();
    Instant now = Instant.now();
    OAuth2AccessToken accessToken =
        new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            "access-token",
            now,
            now.plusSeconds(60),
            grantedScopes);
    OidcIdToken idToken =
        OidcIdToken.withTokenValue("id-token")
            .subject("jane")
            .claim("email", EMAIL)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(60))
            .build();
    return new OidcUserRequest(registration, accessToken, idToken);
  }
}
