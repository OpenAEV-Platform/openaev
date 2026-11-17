package io.openaev.secrets.provider.impl.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.IntegrationTest;
import io.openaev.database.model.ApiTokenSecret;
import io.openaev.secrets.provider.SecretResolvedValue;
import io.openaev.secrets.provider.SecretStoreRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
class ApiTokenHandlerTest extends IntegrationTest {

  private static final String TOKEN = "sk-test-token";

  @Autowired private ApiTokenHandler handler;

  private static SecretStoreRequest request(String apiToken) {
    return SecretStoreRequest.builder().apiToken(apiToken).build();
  }

  @Test
  @DisplayName("Given a token, should store it encrypted and resolve it back")
  void given_token_should_storeEncryptedAndResolve() {
    // Act
    ApiTokenSecret secret = (ApiTokenSecret) handler.buildOrUpdate(null, request(TOKEN));
    SecretResolvedValue resolved = handler.toResolvedValue(secret);

    // Assert
    assertThat(secret.getApiToken()).isNotBlank().isNotEqualTo(TOKEN);
    assertThat(resolved.value()).isEqualTo(new SecretResolvedValue.Value.ApiToken(TOKEN));
  }

  @Test
  @DisplayName("Given no token on update, should keep the stored token")
  void given_noTokenOnUpdate_should_keepStoredToken() {
    // Arrange
    ApiTokenSecret existing = (ApiTokenSecret) handler.buildOrUpdate(null, request(TOKEN));

    // Act
    ApiTokenSecret updated = (ApiTokenSecret) handler.buildOrUpdate(existing, request(null));

    // Assert
    assertThat(handler.toResolvedValue(updated).value())
        .isEqualTo(new SecretResolvedValue.Value.ApiToken(TOKEN));
  }

  @Test
  @DisplayName("Given no token on create, should be rejected")
  void given_noTokenOnCreate_should_beRejected() {
    assertThatThrownBy(() -> handler.buildOrUpdate(null, request(null)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("Given a blank token, should be rejected even on update")
  void given_blankToken_should_beRejected() {
    // Arrange
    ApiTokenSecret existing = (ApiTokenSecret) handler.buildOrUpdate(null, request(TOKEN));

    // Act & Assert
    assertThatThrownBy(() -> handler.buildOrUpdate(existing, request("  ")))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
