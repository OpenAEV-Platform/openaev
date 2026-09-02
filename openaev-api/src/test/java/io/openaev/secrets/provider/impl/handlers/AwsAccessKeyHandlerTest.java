package io.openaev.secrets.provider.impl.handlers;

import static io.openaev.utils.fixtures.CredentialSecretReferenceFixture.getAwsAccessKeyReference;
import static io.openaev.utils.fixtures.CredentialSecretReferenceFixture.getAwsAssumeRoleReference;
import static io.openaev.utils.fixtures.CredentialSecretReferenceFixture.getHashReference;
import static io.openaev.utils.fixtures.CredentialSecretReferenceFixture.getNonCredentialReference;
import static io.openaev.utils.fixtures.CredentialSecretReferenceFixture.getUsernamePasswordReference;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.AWS_ACCESS_KEY_ID;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.AWS_DEFAULT_REGION;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.AWS_OTHER_ACCESS_KEY_ID;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.AWS_OTHER_REGION;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.AWS_OTHER_SECRET_ACCESS_KEY;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.AWS_SECRET_ACCESS_KEY;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.AWS_SESSION_TOKEN;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.awsAccessKeyRequest;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.awsAccessKeyRequestWithSessionToken;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.awsAssumeRoleStaticAccessKeyRequest;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.emptyRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.IntegrationTest;
import io.openaev.database.model.AwsAccessKeySecret;
import io.openaev.database.model.AwsAssumeRoleSecret;
import io.openaev.database.model.HashSecret;
import io.openaev.database.model.Secret;
import io.openaev.database.model.UsernamePasswordSecret;
import io.openaev.secrets.provider.SecretMetadata;
import io.openaev.secrets.provider.SecretStoreRequest;
import io.openaev.service.connector_instances.NativeEncryptionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
class AwsAccessKeyHandlerTest extends IntegrationTest {

  @Autowired private AwsAccessKeyHandler handler;
  @Autowired private NativeEncryptionService nativeEncryptionService;

  @Nested
  @DisplayName("supports(Secret)")
  class SupportsSecret {

    @Test
    @DisplayName("Given an AWS access key secret, should be supported")
    void given_awsAccessKeySecret_should_beSupported() {
      // Arrange
      Secret secret = new AwsAccessKeySecret();

      // Act
      boolean supported = handler.supports(secret);

      // Assert
      assertThat(supported).isTrue();
    }

    @Test
    @DisplayName("Given another AWS secret type, should not be supported")
    void given_awsAssumeRoleSecret_should_notBeSupported() {
      // Arrange
      Secret secret = new AwsAssumeRoleSecret();

      // Act
      boolean supported = handler.supports(secret);

      // Assert
      assertThat(supported).isFalse();
    }

    @Test
    @DisplayName("Given a non AWS secret type, should not be supported")
    void given_nonAwsSecret_should_notBeSupported() {
      // Arrange & Act & Assert
      assertThat(handler.supports(new UsernamePasswordSecret())).isFalse();
      assertThat(handler.supports(new HashSecret())).isFalse();
      assertThat(handler.supports(new Secret())).isFalse();
    }

    @Test
    @DisplayName("Given a null secret, should not be supported")
    void given_nullSecret_should_notBeSupported() {
      // Arrange & Act
      boolean supported = handler.supports((Secret) null);

      // Assert
      assertThat(supported).isFalse();
    }
  }

  @Nested
  @DisplayName("supports(SecretReference)")
  class SupportsSecretReference {

    @Test
    @DisplayName("Given an AWS access key credential reference, should be supported")
    void given_awsAccessKeyReference_should_beSupported() {
      // Arrange & Act
      boolean supported = handler.supports(getAwsAccessKeyReference());

      // Assert
      assertThat(supported).isTrue();
    }

    @Test
    @DisplayName("Given another credential auth method, should not be supported")
    void given_otherAuthMethodReference_should_notBeSupported() {
      // Arrange & Act & Assert
      assertThat(handler.supports(getAwsAssumeRoleReference())).isFalse();
      assertThat(handler.supports(getUsernamePasswordReference())).isFalse();
      assertThat(handler.supports(getHashReference())).isFalse();
    }

    @Test
    @DisplayName("Given a non credential reference, should not be supported")
    void given_nonCredentialReference_should_notBeSupported() {
      // Arrange & Act
      boolean supported = handler.supports(getNonCredentialReference());

      // Assert
      assertThat(supported).isFalse();
    }

    @Test
    @DisplayName("Given a credential reference without auth method, should not be supported")
    void given_referenceWithoutAuthMethod_should_notBeSupported() {
      // Arrange
      var reference = getAwsAccessKeyReference();
      reference.setCredentialAuthMethod(null);

      // Act
      boolean supported = handler.supports(reference);

      // Assert
      assertThat(supported).isFalse();
    }
  }

  @Nested
  @DisplayName("buildOrUpdate - creation")
  class BuildOrUpdateCreation {

    @Test
    @DisplayName("Given a complete request and no existing secret, should build a new secret")
    void given_completeRequestAndNoExistingSecret_should_buildNewSecret() {
      // Arrange
      SecretStoreRequest request = awsAccessKeyRequest();

      // Act
      Secret secret = handler.buildOrUpdate(null, request);

      // Assert
      assertThat(secret).isInstanceOf(AwsAccessKeySecret.class);
      AwsAccessKeySecret awsSecret = (AwsAccessKeySecret) secret;
      assertThat(awsSecret.getAwsDefaultRegion()).isEqualTo(AWS_DEFAULT_REGION);
      assertThat(awsSecret.getAwsAccessKeyId()).isEqualTo(AWS_ACCESS_KEY_ID);
      assertThat(nativeEncryptionService.decrypt(awsSecret.getAwsSecretAccessKey()))
          .isEqualTo(AWS_SECRET_ACCESS_KEY);
      assertThat(awsSecret.getAwsSessionToken()).isNull();
    }

    @Test
    @DisplayName("Given a request with a session token, should encrypt the session token")
    void given_requestWithSessionToken_should_encryptSessionToken() {
      // Arrange
      SecretStoreRequest request = awsAccessKeyRequestWithSessionToken();

      // Act
      AwsAccessKeySecret awsSecret = (AwsAccessKeySecret) handler.buildOrUpdate(null, request);

      // Assert
      assertThat(awsSecret.getAwsSessionToken()).isNotEqualTo(AWS_SESSION_TOKEN);
      assertThat(nativeEncryptionService.decrypt(awsSecret.getAwsSessionToken()))
          .isEqualTo(AWS_SESSION_TOKEN);
    }

    @Test
    @DisplayName("Given a request, should never store the secret access key in clear text")
    void given_request_should_notStoreSecretAccessKeyInClearText() {
      // Arrange
      SecretStoreRequest request = awsAccessKeyRequest();

      // Act
      AwsAccessKeySecret awsSecret = (AwsAccessKeySecret) handler.buildOrUpdate(null, request);

      // Assert
      assertThat(awsSecret.getAwsSecretAccessKey()).isNotEqualTo(AWS_SECRET_ACCESS_KEY);
      assertThat(awsSecret.getAwsAccessKeyId()).isEqualTo(AWS_ACCESS_KEY_ID);
    }

    @Test
    @DisplayName("Given an existing secret of another type, should build a brand new secret")
    void given_existingSecretOfAnotherType_should_buildNewSecret() {
      // Arrange
      AwsAssumeRoleSecret existingSecret = new AwsAssumeRoleSecret();
      existingSecret.setAwsRoleArn("arn:aws:iam::123456789012:role/should-be-ignored");

      // Act
      Secret secret = handler.buildOrUpdate(existingSecret, awsAccessKeyRequest());

      // Assert
      assertThat(secret).isInstanceOf(AwsAccessKeySecret.class).isNotSameAs(existingSecret);
    }
  }

  @Nested
  @DisplayName("buildOrUpdate - update")
  class BuildOrUpdateUpdate {

    private AwsAccessKeySecret existingSecret() {
      return (AwsAccessKeySecret)
          handler.buildOrUpdate(null, awsAccessKeyRequestWithSessionToken());
    }

    @Test
    @DisplayName("Given an existing secret, should update it in place")
    void given_existingSecret_should_updateItInPlace() {
      // Arrange
      AwsAccessKeySecret existingSecret = existingSecret();
      SecretStoreRequest request =
          awsAccessKeyRequest(
              AWS_OTHER_REGION, AWS_OTHER_ACCESS_KEY_ID, AWS_OTHER_SECRET_ACCESS_KEY, null);

      // Act
      Secret secret = handler.buildOrUpdate(existingSecret, request);

      // Assert
      assertThat(secret).isSameAs(existingSecret);
      assertThat(existingSecret.getAwsDefaultRegion()).isEqualTo(AWS_OTHER_REGION);
      assertThat(existingSecret.getAwsAccessKeyId()).isEqualTo(AWS_OTHER_ACCESS_KEY_ID);
      assertThat(nativeEncryptionService.decrypt(existingSecret.getAwsSecretAccessKey()))
          .isEqualTo(AWS_OTHER_SECRET_ACCESS_KEY);
    }

    @Test
    @DisplayName("Given a partial request, should keep the fields that were not provided")
    void given_partialRequest_should_keepNotProvidedFields() {
      // Arrange
      AwsAccessKeySecret existingSecret = existingSecret();
      SecretStoreRequest request = awsAccessKeyRequest(AWS_OTHER_REGION, null, null, null);

      // Act
      AwsAccessKeySecret awsSecret =
          (AwsAccessKeySecret) handler.buildOrUpdate(existingSecret, request);

      // Assert
      assertThat(awsSecret.getAwsDefaultRegion()).isEqualTo(AWS_OTHER_REGION);
      assertThat(awsSecret.getAwsAccessKeyId()).isEqualTo(AWS_ACCESS_KEY_ID);
      assertThat(nativeEncryptionService.decrypt(awsSecret.getAwsSecretAccessKey()))
          .isEqualTo(AWS_SECRET_ACCESS_KEY);
      assertThat(nativeEncryptionService.decrypt(awsSecret.getAwsSessionToken()))
          .isEqualTo(AWS_SESSION_TOKEN);
    }

    @Test
    @DisplayName("Given an empty request on a complete secret, should leave it untouched")
    void given_emptyRequestOnCompleteSecret_should_leaveItUntouched() {
      // Arrange
      AwsAccessKeySecret existingSecret = existingSecret();
      String encryptedSecretAccessKey = existingSecret.getAwsSecretAccessKey();

      // Act
      AwsAccessKeySecret awsSecret =
          (AwsAccessKeySecret) handler.buildOrUpdate(existingSecret, emptyRequest());

      // Assert
      assertThat(awsSecret.getAwsDefaultRegion()).isEqualTo(AWS_DEFAULT_REGION);
      assertThat(awsSecret.getAwsAccessKeyId()).isEqualTo(AWS_ACCESS_KEY_ID);
      assertThat(awsSecret.getAwsSecretAccessKey()).isEqualTo(encryptedSecretAccessKey);
    }

    @Test
    @DisplayName("Given a new secret access key, should re-encrypt it with a new cipher text")
    void given_newSecretAccessKey_should_reEncryptIt() {
      // Arrange
      AwsAccessKeySecret existingSecret = existingSecret();
      String previousCipherText = existingSecret.getAwsSecretAccessKey();
      SecretStoreRequest request =
          awsAccessKeyRequest(null, null, AWS_OTHER_SECRET_ACCESS_KEY, null);

      // Act
      AwsAccessKeySecret awsSecret =
          (AwsAccessKeySecret) handler.buildOrUpdate(existingSecret, request);

      // Assert
      assertThat(awsSecret.getAwsSecretAccessKey()).isNotEqualTo(previousCipherText);
      assertThat(nativeEncryptionService.decrypt(awsSecret.getAwsSecretAccessKey()))
          .isEqualTo(AWS_OTHER_SECRET_ACCESS_KEY);
    }
  }

  @Nested
  @DisplayName("buildOrUpdate - validation")
  class BuildOrUpdateValidation {

    @Test
    @DisplayName("Given a request without region, should throw")
    void given_requestWithoutRegion_should_throw() {
      // Arrange
      SecretStoreRequest request =
          awsAccessKeyRequest(null, AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, null);

      // Act & Assert
      assertThatThrownBy(() -> handler.buildOrUpdate(null, request))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("AWS default region, access key id and secret are required");
    }

    @Test
    @DisplayName("Given a request without access key id, should throw")
    void given_requestWithoutAccessKeyId_should_throw() {
      // Arrange
      SecretStoreRequest request =
          awsAccessKeyRequest(AWS_DEFAULT_REGION, null, AWS_SECRET_ACCESS_KEY, null);

      // Act & Assert
      assertThatThrownBy(() -> handler.buildOrUpdate(null, request))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("AWS default region, access key id and secret are required");
    }

    @Test
    @DisplayName("Given a request without secret access key, should throw")
    void given_requestWithoutSecretAccessKey_should_throw() {
      // Arrange
      SecretStoreRequest request =
          awsAccessKeyRequest(AWS_DEFAULT_REGION, AWS_ACCESS_KEY_ID, null, null);

      // Act & Assert
      assertThatThrownBy(() -> handler.buildOrUpdate(null, request))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("AWS default region, access key id and secret are required");
    }

    @Test
    @DisplayName("Given an empty request and no existing secret, should throw")
    void given_emptyRequestAndNoExistingSecret_should_throw() {
      // Arrange & Act & Assert
      assertThatThrownBy(() -> handler.buildOrUpdate(null, emptyRequest()))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Given a request for another auth method, should throw")
    void given_assumeRoleRequest_should_throw() {
      // Arrange
      SecretStoreRequest request = awsAssumeRoleStaticAccessKeyRequest();

      // Act & Assert
      assertThatThrownBy(() -> handler.buildOrUpdate(null, request))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("toMetadata")
  class ToMetadata {

    @Test
    @DisplayName("Given an AWS access key secret, should expose region and access key id only")
    void given_awsAccessKeySecret_should_exposeRegionAndAccessKeyIdOnly() {
      // Arrange
      AwsAccessKeySecret secret =
          (AwsAccessKeySecret) handler.buildOrUpdate(null, awsAccessKeyRequestWithSessionToken());

      // Act
      SecretMetadata metadata = handler.toMetadata(secret);

      // Assert
      assertThat(metadata.awsDefaultRegion()).isEqualTo(AWS_DEFAULT_REGION);
      assertThat(metadata.awsAccessKeyId()).isEqualTo(AWS_ACCESS_KEY_ID);
      assertThat(metadata.username()).isNull();
      assertThat(metadata.hashAlgorithm()).isNull();
      assertThat(metadata.awsRoleArn()).isNull();
      assertThat(metadata.awsSourceIdentityType()).isNull();
      assertThat(metadata.awsSourceProfileAccessKeyId()).isNull();
    }

    @Test
    @DisplayName("Given an AWS access key secret, should never expose any secret value")
    void given_awsAccessKeySecret_should_notExposeSecretValues() {
      // Arrange
      AwsAccessKeySecret secret =
          (AwsAccessKeySecret) handler.buildOrUpdate(null, awsAccessKeyRequestWithSessionToken());

      // Act
      SecretMetadata metadata = handler.toMetadata(secret);

      // Assert
      assertThat(metadata.toString())
          .doesNotContain(AWS_SECRET_ACCESS_KEY)
          .doesNotContain(AWS_SESSION_TOKEN)
          .doesNotContain(secret.getAwsSecretAccessKey())
          .doesNotContain(secret.getAwsSessionToken());
    }

    @Test
    @DisplayName("Given a secret of another type, should throw")
    void given_secretOfAnotherType_should_throw() {
      // Arrange
      Secret secret = new AwsAssumeRoleSecret();

      // Act & Assert
      assertThatThrownBy(() -> handler.toMetadata(secret))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Secret type mismatch: expected AWS_ACCESS_KEY secret");
    }

    @Test
    @DisplayName("Given a null secret, should throw")
    void given_nullSecret_should_throw() {
      // Arrange & Act & Assert
      assertThatThrownBy(() -> handler.toMetadata(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Secret type mismatch: expected AWS_ACCESS_KEY secret");
    }
  }
}
