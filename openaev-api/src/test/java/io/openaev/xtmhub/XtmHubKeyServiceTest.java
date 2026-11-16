package io.openaev.xtmhub;

import static io.openaev.database.model.SettingKeys.XTM_HUB_KEY_VERSION;
import static io.openaev.xtmhub.XtmHubKeyService.ASSERTION_AUDIENCE;
import static io.openaev.xtmhub.XtmHubKeyService.ASSERTION_IDENTIFIER;
import static io.openaev.xtmhub.XtmHubKeyService.ASSERTION_LIFETIME;
import static io.openaev.xtmhub.XtmHubKeyService.IDENTIFIER_CLAIM;
import static io.openaev.xtmhub.XtmHubKeyService.TENANT_ID_CLAIM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Jwks;
import io.jsonwebtoken.security.PublicJwk;
import io.openaev.config.OpenAEVAdminConfig;
import io.openaev.database.model.Setting;
import io.openaev.service.PlatformSettingsService;
import java.security.PublicKey;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("XTM Hub key of the instance")
class XtmHubKeyServiceTest {

  private static final String ENCRYPTION_KEY = "ThisIsMyUltraSecureEncryptionKey";
  private static final String PLATFORM_ID = "b4a5e3c2-7f0e-4c39-9d6e-2f1a8c3b5d71";
  private static final String TENANT_ID = "2cffad3a-0001-4078-b0e2-ef74274022c3";

  // Known answers for ENCRYPTION_KEY, computed with an independent HKDF-SHA256 and RFC 8032
  // implementation. If they change, every enrolled instance loses its key at upgrade.
  private static final String V1_X = "6UCI9WL5fDTVvWGB-ZW6Hg5mPouKUaKiZZzr_yh7PM4";
  private static final String V1_KID = "yWKtbQNkDKSxL5N_yoKGIHRwI-o6Dln4FFelOMAbPa0";
  private static final String XTM_AUTH_X = "Tr34EPwiRsBzLbhMj4bvoCx2_EEw9dhshRsokcpW10k";

  /** A node of the instance, started fresh: nothing is shared with another node but settings. */
  private static XtmHubKeyService node(Optional<String> storedVersion) {
    OpenAEVAdminConfig adminConfig = mock(OpenAEVAdminConfig.class);
    // Lenient: a malformed version is refused before the key is derived.
    lenient().when(adminConfig.getEncryptionKey()).thenReturn(ENCRYPTION_KEY);
    PlatformSettingsService platformSettingsService = mock(PlatformSettingsService.class);
    when(platformSettingsService.setting(XTM_HUB_KEY_VERSION.key()))
        .thenReturn(storedVersion.map(v -> new Setting(XTM_HUB_KEY_VERSION.key(), v)));
    return new XtmHubKeyService(adminConfig, platformSettingsService);
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

      // Act
      XtmHubPublicJwk first = nodeWithoutSetting.publicJwk();
      XtmHubPublicJwk second = nodeAtVersionOne.publicJwk();

      // Assert
      assertThat(first).isEqualTo(second);
      assertThat(first)
          .isEqualTo(new XtmHubPublicJwk("OKP", "Ed25519", "EdDSA", "sig", V1_KID, V1_X));
    }

    @Test
    @DisplayName("the next version gives another key")
    void given_nextVersion_should_deriveAnotherKey() {
      // Act
      XtmHubPublicJwk versionOne = node(Optional.of("1")).publicJwk();
      XtmHubPublicJwk versionTwo = node(Optional.of("2")).publicJwk();

      // Assert
      assertThat(versionTwo.x()).isNotEqualTo(versionOne.x());
      assertThat(versionTwo.kid()).isNotEqualTo(versionOne.kid());
    }

    @Test
    @DisplayName("the same encryption key never gives the XTM auth key")
    void given_sameEncryptionKey_should_notReuseXtmAuthKey() {
      // Act
      XtmHubPublicJwk jwk = node(Optional.empty()).publicJwk();

      // Assert
      assertThat(jwk.x()).isNotEqualTo(XTM_AUTH_X);
    }

    @ParameterizedTest(name = "version \"{0}\"")
    @ValueSource(strings = {"0", "-1", "two", ""})
    @DisplayName("a malformed version is refused, never replaced by the default")
    void given_malformedVersion_should_refuse(String storedVersion) {
      // Arrange
      XtmHubKeyService service = node(Optional.of(storedVersion));

      // Act & Assert
      assertThatThrownBy(service::publicJwk)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining(XTM_HUB_KEY_VERSION.key());
    }
  }

  @Nested
  @DisplayName("Assertion")
  class Assertion {

    @ParameterizedTest(name = "tenant {0}")
    @NullSource
    @ValueSource(strings = TENANT_ID)
    @DisplayName("a signed assertion verifies with the public JWK alone")
    void given_signedAssertion_should_verifyWithPublicJwk(String tenantId) throws Exception {
      // Arrange
      XtmHubKeyService service = node(Optional.empty());
      String publishedJwk = new ObjectMapper().writeValueAsString(service.publicJwk());
      PublicKey verificationKey =
          ((PublicJwk<?>) Jwks.parser().build().parse(publishedJwk)).toKey();

      // Act
      String assertion = service.signAssertion(PLATFORM_ID, tenantId);
      String another = service.signAssertion(PLATFORM_ID, tenantId);

      // Assert
      Jws<Claims> verified = verify(assertion, verificationKey);
      assertThat(verified.getHeader().getAlgorithm()).isEqualTo("EdDSA");
      assertThat(verified.getHeader().getKeyId()).isEqualTo(V1_KID);
      assertThat(verified.getHeader().getType()).isEqualTo("JWT");
      Claims claims = verified.getPayload();
      assertThat(claims.get(IDENTIFIER_CLAIM, String.class)).isEqualTo(ASSERTION_IDENTIFIER);
      assertThat(claims.get(TENANT_ID_CLAIM, String.class)).isEqualTo(tenantId);
      assertThat(claims.getExpiration().getTime() - claims.getIssuedAt().getTime())
          .isEqualTo(ASSERTION_LIFETIME.toMillis());
      assertThat(claims.getId()).isNotBlank();
      assertThat(verify(another, verificationKey).getPayload().getId())
          .isNotEqualTo(claims.getId());
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
