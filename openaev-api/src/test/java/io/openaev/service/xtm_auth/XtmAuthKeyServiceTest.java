package io.openaev.service.xtm_auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.openaev.config.OpenAEVAdminConfig;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("XTM auth key of the instance")
public class XtmAuthKeyServiceTest {

  public static final String ENCRYPTION_KEY = "ThisIsMyUltraSecureEncryptionKey";

  // Known answers for ENCRYPTION_KEY, computed with an independent HKDF-SHA256 and RFC 8032
  // implementation. If they change, the products that trust this key reject its tokens.
  public static final String XTM_AUTH_X = "Tr34EPwiRsBzLbhMj4bvoCx2_EEw9dhshRsokcpW10k";
  private static final String XTM_AUTH_KID = "TiEZVF8PO_r3UyRZP4s05EGd7Q4mUERBCDcVsdY6bnI";

  private static final int ED25519_RAW_KEY_LENGTH = 32;

  @Test
  @DisplayName("the encryption key always gives the same key")
  void given_encryptionKey_should_deriveKnownKey() {
    // Arrange
    OpenAEVAdminConfig adminConfig = mock(OpenAEVAdminConfig.class);
    when(adminConfig.getEncryptionKey()).thenReturn(ENCRYPTION_KEY);
    XtmAuthKeyService service = new XtmAuthKeyService(adminConfig);

    // Act
    service.init();

    // Assert
    byte[] encoded = service.getKeyPair().getPublic().getEncoded();
    byte[] rawPublicKey =
        Arrays.copyOfRange(encoded, encoded.length - ED25519_RAW_KEY_LENGTH, encoded.length);
    assertThat(Base64.getUrlEncoder().withoutPadding().encodeToString(rawPublicKey))
        .isEqualTo(XTM_AUTH_X);
    assertThat(service.getKid()).isEqualTo(XTM_AUTH_KID);
  }
}
