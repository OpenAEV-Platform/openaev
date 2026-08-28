package io.openaev.service.connector_instances;

import static io.openaev.utils.fixtures.SecretStoreRequestFixture.GCP_PRIVATE_KEY_JSON;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Native encryption service tests")
class NativeEncryptionServiceTest extends IntegrationTest {

  /**
   * A key-file sized payload (~2.3 KB), the payload the binary overloads exist for. Deliberately
   * built from a synthetic filler instead of a credential-shaped literal, so the file carries
   * nothing that secret scanning can mistake for a real key.
   */
  private static final String KEY_FILE_SIZED_PAYLOAD = buildKeyFileSizedPayload();

  @Autowired private NativeEncryptionService nativeEncryptionService;

  private static String buildKeyFileSizedPayload() {
    // 34 lines of 64 base64-like characters, the size a serialized key file has in practice.
    String fillerLine = "QUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVphYmNkZWZnaGlqa2xtbm9wcXJzdHV2";
    StringBuilder blob = new StringBuilder();
    for (int i = 0; i < 34; i++) {
      blob.append(fillerLine).append("\\n");
    }

    return """
        {
          "kind": "openaev#test-payload",
          "project_id": "openaev-simulation",
          "material_id": "1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b",
          "material": "%s",
          "owner_email": "openaev@example.invalid",
          "owner_id": "123456789012345678901"
        }
        """
        .formatted(blob);
  }

  @Nested
  @DisplayName("byte[] round trip")
  class BytesRoundTrip {

    @Test
    @DisplayName("given_keyFileSizedPayload_should_decryptBackToTheSameBytes")
    void given_keyFileSizedPayload_should_decryptBackToTheSameBytes() {
      // Arrange
      byte[] plainBytes = KEY_FILE_SIZED_PAYLOAD.getBytes(StandardCharsets.UTF_8);

      // Act
      byte[] decrypted =
          nativeEncryptionService.decrypt(nativeEncryptionService.encrypt(plainBytes));

      // Assert
      assertThat(plainBytes.length).isBetween(2_000, 3_000);
      assertThat(decrypted).isEqualTo(plainBytes);
      assertThat(new String(decrypted, StandardCharsets.UTF_8)).isEqualTo(KEY_FILE_SIZED_PAYLOAD);
    }

    @Test
    @DisplayName("given_shortPayload_should_decryptBackToTheSameBytes")
    void given_shortPayload_should_decryptBackToTheSameBytes() {
      // Arrange
      byte[] plainBytes = GCP_PRIVATE_KEY_JSON.getBytes(StandardCharsets.UTF_8);

      // Act
      byte[] decrypted =
          nativeEncryptionService.decrypt(nativeEncryptionService.encrypt(plainBytes));

      // Assert
      assertThat(decrypted).isEqualTo(plainBytes);
    }

    @Test
    @DisplayName("given_binaryPayload_should_decryptBackToTheSameBytes")
    void given_binaryPayload_should_decryptBackToTheSameBytes() {
      // Arrange: not every byte sequence is valid UTF-8, and the column is a BYTEA
      byte[] plainBytes = new byte[256];
      for (int i = 0; i < plainBytes.length; i++) {
        plainBytes[i] = (byte) i;
      }

      // Act
      byte[] decrypted =
          nativeEncryptionService.decrypt(nativeEncryptionService.encrypt(plainBytes));

      // Assert
      assertThat(decrypted).isEqualTo(plainBytes);
    }

    @Test
    @DisplayName("given_samePayloadTwice_should_produceDifferentCipherTexts")
    void given_samePayloadTwice_should_produceDifferentCipherTexts() {
      // Arrange
      byte[] plainBytes = GCP_PRIVATE_KEY_JSON.getBytes(StandardCharsets.UTF_8);

      // Act
      byte[] first = nativeEncryptionService.encrypt(plainBytes);
      byte[] second = nativeEncryptionService.encrypt(plainBytes);

      // Assert: a random IV per call, so a stored cipher text leaks nothing by comparison
      assertThat(first).isNotEqualTo(second);
      assertThat(nativeEncryptionService.decrypt(first)).isEqualTo(plainBytes);
      assertThat(nativeEncryptionService.decrypt(second)).isEqualTo(plainBytes);
    }

    @Test
    @DisplayName("given_payload_should_neverStoreItInClearText")
    void given_payload_should_neverStoreItInClearText() {
      // Arrange
      byte[] plainBytes = GCP_PRIVATE_KEY_JSON.getBytes(StandardCharsets.UTF_8);

      // Act
      byte[] encrypted = nativeEncryptionService.encrypt(plainBytes);

      // Assert
      assertThat(encrypted).isNotEqualTo(plainBytes);
      assertThat(new String(encrypted, StandardCharsets.UTF_8)).doesNotContain("private_key");
    }
  }
}
