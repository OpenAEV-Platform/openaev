package io.openaev.xtmhub;

import static io.openaev.database.model.SettingKeys.XTM_HUB_KEY_VERSION;
import static io.openaev.service.xtm_auth.XtmAuthKeyServiceTest.ENCRYPTION_KEY;
import static io.openaev.service.xtm_auth.XtmAuthKeyServiceTest.XTM_AUTH_X;
import static io.openaev.xtmhub.XtmHubKeyService.ASSERTION_AUDIENCE;
import static io.openaev.xtmhub.XtmHubKeyService.ASSERTION_IDENTIFIER;
import static io.openaev.xtmhub.XtmHubKeyService.ASSERTION_TYPE;
import static io.openaev.xtmhub.XtmHubKeyService.IDENTIFIER_CLAIM;
import static io.openaev.xtmhub.XtmHubKeyService.TENANT_ID_CLAIM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Jwks;
import io.jsonwebtoken.security.PublicJwk;
import io.openaev.api.jwks.JwkOutput;
import io.openaev.config.OpenAEVAdminConfig;
import io.openaev.database.model.Setting;
import io.openaev.database.model.Tenant;
import io.openaev.service.PlatformSettingsService;
import io.openaev.service.tenants.TenantService;
import java.security.PublicKey;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("XTM Hub keys of the tenants")
class XtmHubKeyServiceTest {

  private static final String PLATFORM_ID = "b4a5e3c2-7f0e-4c39-9d6e-2f1a8c3b5d71";
  private static final String TENANT_ID = "2cffad3a-0001-4078-b0e2-ef74274022c3";
  private static final String OTHER_TENANT_ID = "7e1c4d2a-5b3f-4f6e-9a08-1d2c3b4a5f60";

  // Known answers for ENCRYPTION_KEY at version 1, computed with an independent HKDF-SHA256 and RFC
  // 8032 implementation. If they change, every enrolled tenant loses its key at upgrade.
  private static final JwkOutput TENANT_V1 =
      published(
          "xIXydb6oMp72wIB3ssABQ3qj9RTGsWGrdpLerVtsKxQ",
          "TZ2zxIPB9wYrTjqJCnPzSFp43_yf9uTCKJBY4nS_eKk");
  private static final JwkOutput OTHER_TENANT_V1 =
      published(
          "jVdbwGCZRcBXTkBxwSxav6utC277e4MOpBn6Iavi9kk",
          "VvIyPwdf8JWghG2L4-hAm80l8XcNAnZLUzvyd-4qGKY");

  private static JwkOutput published(String kid, String x) {
    return new JwkOutput("OKP", "Ed25519", x, kid, null, "EdDSA", "sig");
  }

  private static JwkOutput tenantKey(XtmHubKeyService service, String tenantId) {
    return JwkOutput.from(service.tenantPublicJwk(tenantId).orElseThrow());
  }

  /** A node of the instance, started fresh: nothing is shared with another node but settings. */
  private static XtmHubKeyService node(Optional<String> storedVersion) {
    TenantService tenants = mock(TenantService.class);
    lenient().when(tenants.findActiveById(anyString())).thenReturn(Optional.of(new Tenant()));
    return node(storedVersion, tenants);
  }

  private static XtmHubKeyService node(Optional<String> storedVersion, TenantService tenants) {
    return node(storedVersion, tenants, ENCRYPTION_KEY);
  }

  private static XtmHubKeyService node(
      Optional<String> storedVersion, TenantService tenants, String encryptionKey) {
    OpenAEVAdminConfig adminConfig = mock(OpenAEVAdminConfig.class);
    // Lenient: a malformed version, or an unknown tenant, is refused before a key is derived.
    lenient().when(adminConfig.getEncryptionKey()).thenReturn(encryptionKey);
    PlatformSettingsService platformSettingsService = mock(PlatformSettingsService.class);
    lenient()
        .when(platformSettingsService.setting(XTM_HUB_KEY_VERSION.key()))
        .thenReturn(storedVersion.map(v -> new Setting(XTM_HUB_KEY_VERSION.key(), v)));
    return new XtmHubKeyService(adminConfig, platformSettingsService, tenants);
  }

  @Nested
  @DisplayName("Key derivation")
  class KeyDerivation {

    @Test
    @DisplayName("the same version gives the same key on every node and after a restart")
    void given_sameVersion_should_deriveSameKey() {
      // Arrange
      XtmHubKeyService nodeWithoutSetting = node(Optional.empty());
      XtmHubKeyService nodeAtVersionOne = node(Optional.of("1"));

      // Act & Assert
      assertThat(tenantKey(nodeWithoutSetting, TENANT_ID))
          .isEqualTo(tenantKey(nodeAtVersionOne, TENANT_ID))
          .isEqualTo(TENANT_V1);
    }

    @Test
    @DisplayName("each tenant has its own key")
    void given_differentTenants_should_deriveDifferentKeys() {
      // Arrange
      XtmHubKeyService service = node(Optional.empty());

      // Act
      JwkOutput tenant = tenantKey(service, TENANT_ID);
      JwkOutput otherTenant = tenantKey(service, OTHER_TENANT_ID);

      // Assert
      assertThat(otherTenant).isEqualTo(OTHER_TENANT_V1);
      assertThat(otherTenant.x()).isNotEqualTo(tenant.x());
      assertThat(otherTenant.kid()).isNotEqualTo(tenant.kid());
    }

    @Test
    @DisplayName("the next version gives another key")
    void given_nextVersion_should_deriveAnotherKey() {
      // Arrange
      XtmHubKeyService versionOne = node(Optional.of("1"));
      XtmHubKeyService versionTwo = node(Optional.of("2"));

      // Act & Assert
      assertThat(tenantKey(versionTwo, TENANT_ID).x())
          .isNotEqualTo(tenantKey(versionOne, TENANT_ID).x());
    }

    @Test
    @DisplayName("the same encryption key never gives the XTM auth key")
    void given_sameEncryptionKey_should_notReuseXtmAuthKey() {
      // Arrange
      XtmHubKeyService service = node(Optional.empty());

      // Act & Assert
      assertThat(tenantKey(service, TENANT_ID).x()).isNotEqualTo(XTM_AUTH_X);
    }

    @ParameterizedTest(name = "version \"{0}\"")
    @ValueSource(strings = {"0", "-1", "two", ""})
    @DisplayName("a malformed version is refused, never replaced by the default")
    void given_malformedVersion_should_refuse(String storedVersion) {
      // Arrange
      XtmHubKeyService service = node(Optional.of(storedVersion));

      // Act & Assert
      assertThatThrownBy(() -> tenantKey(service, TENANT_ID))
          .isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest(name = "encryption key \"{0}\"")
    @ValueSource(strings = {"ChangeMe", "ThisKeyIsOneCharacterTooShort31"})
    @DisplayName("a guessable or short encryption key gives no key and signs nothing")
    void given_shortEncryptionKey_should_refuse(String encryptionKey) {
      // Arrange
      TenantService tenants = mock(TenantService.class);
      lenient().when(tenants.findActiveById(anyString())).thenReturn(Optional.of(new Tenant()));
      XtmHubKeyService service = node(Optional.empty(), tenants, encryptionKey);

      // Act & Assert
      assertThatThrownBy(() -> service.tenantPublicJwk(TENANT_ID))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageNotContaining(encryptionKey);
      assertThatThrownBy(() -> service.signAssertion(PLATFORM_ID, TENANT_ID))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageNotContaining(encryptionKey);
    }

    @Test
    @DisplayName("an unknown or deleted tenant is given no key")
    void given_unknownOrDeletedTenant_should_giveNoKey() {
      // Arrange
      TenantService tenants = mock(TenantService.class);
      when(tenants.findActiveById(OTHER_TENANT_ID)).thenReturn(Optional.empty());
      XtmHubKeyService service = node(Optional.empty(), tenants);

      // Act & Assert
      assertThat(service.tenantPublicJwk(OTHER_TENANT_ID)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Assertion")
  class Assertion {

    @Test
    @DisplayName("a signed assertion verifies with the public JWK of its own tenant alone")
    void given_signedAssertion_should_verifyWithPublicJwk() throws Exception {
      // Arrange
      XtmHubKeyService service = node(Optional.empty());
      JwkOutput published = tenantKey(service, TENANT_ID);

      // Act
      String assertion = service.signAssertion(PLATFORM_ID, TENANT_ID);
      String another = service.signAssertion(PLATFORM_ID, TENANT_ID);

      // Assert
      PublicKey verificationKey = verificationKey(published);
      Jws<Claims> verified = verify(assertion, verificationKey);
      assertThat(verified.getHeader().getAlgorithm()).isEqualTo("EdDSA");
      assertThat(verified.getHeader().getKeyId()).isEqualTo(published.kid());
      assertThat(verified.getHeader().getType()).isEqualTo(ASSERTION_TYPE);
      Claims claims = verified.getPayload();
      assertThat(claims.get(IDENTIFIER_CLAIM, String.class)).isEqualTo(ASSERTION_IDENTIFIER);
      assertThat(claims.get(TENANT_ID_CLAIM, String.class)).isEqualTo(TENANT_ID);
      assertThat(claims.getExpiration().getTime() - claims.getIssuedAt().getTime())
          .isEqualTo(Duration.ofSeconds(60).toMillis());
      assertThat(claims.getId()).isNotBlank();
      assertThat(verify(another, verificationKey).getPayload().getId())
          .isNotEqualTo(claims.getId());
    }

    @Test
    @DisplayName("an assertion of one tenant does not verify with the key of another")
    void given_tenantAssertion_should_notVerifyWithAnotherTenantKey() throws Exception {
      // Arrange
      XtmHubKeyService service = node(Optional.empty());
      String assertion = service.signAssertion(PLATFORM_ID, TENANT_ID);

      // Act & Assert
      assertThat(verify(assertion, verificationKey(tenantKey(service, TENANT_ID)))).isNotNull();
      assertThatThrownBy(
              () -> verify(assertion, verificationKey(tenantKey(service, OTHER_TENANT_ID))))
          .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("a call with no tenant is not signed")
    void given_noTenant_should_refuseToSign() {
      // Arrange
      XtmHubKeyService service = node(Optional.empty());

      // Act & Assert
      assertThatThrownBy(() -> service.signAssertion(PLATFORM_ID, null))
          .isInstanceOf(NullPointerException.class);
    }

    /** The key a verifier builds from the JWK the instance publishes, and nothing else. */
    private PublicKey verificationKey(JwkOutput key) throws Exception {
      String publishedJwk = new ObjectMapper().writeValueAsString(key);
      return ((PublicJwk<?>) Jwks.parser().build().parse(publishedJwk)).toKey();
    }

    private Jws<Claims> verify(String assertion, PublicKey verificationKey) {
      return Jwts.parser()
          .verifyWith(verificationKey)
          .requireIssuer(PLATFORM_ID)
          .requireAudience(ASSERTION_AUDIENCE)
          .build()
          .parseSignedClaims(assertion);
    }
  }
}
