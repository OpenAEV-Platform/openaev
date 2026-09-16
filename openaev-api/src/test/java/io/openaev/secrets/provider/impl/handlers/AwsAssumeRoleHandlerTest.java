package io.openaev.secrets.provider.impl.handlers;

import static io.openaev.database.model.AwsAssumeRoleSecret.AWS_SOURCE_IDENTITY_TYPE.INSTANCE_DEFAULT;
import static io.openaev.database.model.AwsAssumeRoleSecret.AWS_SOURCE_IDENTITY_TYPE.STATIC_ACCESS_KEY;
import static io.openaev.utils.fixtures.CredentialSecretReferenceFixture.getAwsAccessKeyReference;
import static io.openaev.utils.fixtures.CredentialSecretReferenceFixture.getAwsAssumeRoleReference;
import static io.openaev.utils.fixtures.CredentialSecretReferenceFixture.getHashReference;
import static io.openaev.utils.fixtures.CredentialSecretReferenceFixture.getNonCredentialReference;
import static io.openaev.utils.fixtures.CredentialSecretReferenceFixture.getUsernamePasswordReference;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.AWS_DEFAULT_REGION;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.AWS_EXTERNAL_ID;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.AWS_OTHER_REGION;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.AWS_OTHER_ROLE_ARN;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.AWS_OTHER_SECRET_ACCESS_KEY;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.AWS_ROLE_ARN;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.AWS_SOURCE_PROFILE_ACCESS_KEY_ID;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.AWS_SOURCE_PROFILE_SECRET_ACCESS_KEY;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.awsAccessKeyRequest;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.awsAssumeRoleInstanceDefaultRequest;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.awsAssumeRoleRequest;
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
class AwsAssumeRoleHandlerTest extends IntegrationTest {

  @Autowired private AwsAssumeRoleHandler handler;
  @Autowired private NativeEncryptionService nativeEncryptionService;

  private AwsAssumeRoleSecret staticAccessKeySecret() {
    return (AwsAssumeRoleSecret) handler.buildOrUpdate(null, awsAssumeRoleStaticAccessKeyRequest());
  }

  private AwsAssumeRoleSecret instanceDefaultSecret() {
    return (AwsAssumeRoleSecret) handler.buildOrUpdate(null, awsAssumeRoleInstanceDefaultRequest());
  }

  @Nested
  @DisplayName("supports(Secret)")
  class SupportsSecret {

    @Test
    @DisplayName("Given an AWS assume role secret, should be supported")
    void given_awsAssumeRoleSecret_should_beSupported() {
      // Arrange
      Secret secret = new AwsAssumeRoleSecret();

      // Act
      boolean supported = handler.supports(secret);

      // Assert
      assertThat(supported).isTrue();
    }

    @Test
    @DisplayName("Given another AWS secret type, should not be supported")
    void given_awsAccessKeySecret_should_notBeSupported() {
      // Arrange
      Secret secret = new AwsAccessKeySecret();

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
    @DisplayName("Given an AWS assume role credential reference, should be supported")
    void given_awsAssumeRoleReference_should_beSupported() {
      // Arrange & Act
      boolean supported = handler.supports(getAwsAssumeRoleReference());

      // Assert
      assertThat(supported).isTrue();
    }

    @Test
    @DisplayName("Given another credential auth method, should not be supported")
    void given_otherAuthMethodReference_should_notBeSupported() {
      // Arrange & Act & Assert
      assertThat(handler.supports(getAwsAccessKeyReference())).isFalse();
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
      var reference = getAwsAssumeRoleReference();
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
    @DisplayName("Given an instance default request, should build a secret without source profile")
    void given_instanceDefaultRequest_should_buildSecretWithoutSourceProfile() {
      // Arrange
      SecretStoreRequest request = awsAssumeRoleInstanceDefaultRequest();

      // Act
      Secret secret = handler.buildOrUpdate(null, request);

      // Assert
      assertThat(secret).isInstanceOf(AwsAssumeRoleSecret.class);
      AwsAssumeRoleSecret awsSecret = (AwsAssumeRoleSecret) secret;
      assertThat(awsSecret.getAwsDefaultRegion()).isEqualTo(AWS_DEFAULT_REGION);
      assertThat(awsSecret.getAwsRoleArn()).isEqualTo(AWS_ROLE_ARN);
      assertThat(awsSecret.getAwsSourceIdentityType()).isEqualTo(INSTANCE_DEFAULT);
      assertThat(awsSecret.getAwsSourceProfileAccessKeyId()).isNull();
      assertThat(awsSecret.getAwsSourceProfileSecretAccessKey()).isNull();
      assertThat(nativeEncryptionService.decrypt(awsSecret.getAwsExternalId()))
          .isEqualTo(AWS_EXTERNAL_ID);
    }

    @Test
    @DisplayName("Given a static access key request, should build a secret with a source profile")
    void given_staticAccessKeyRequest_should_buildSecretWithSourceProfile() {
      // Arrange
      SecretStoreRequest request = awsAssumeRoleStaticAccessKeyRequest();

      // Act
      AwsAssumeRoleSecret awsSecret = (AwsAssumeRoleSecret) handler.buildOrUpdate(null, request);

      // Assert
      assertThat(awsSecret.getAwsSourceIdentityType()).isEqualTo(STATIC_ACCESS_KEY);
      assertThat(awsSecret.getAwsSourceProfileAccessKeyId())
          .isEqualTo(AWS_SOURCE_PROFILE_ACCESS_KEY_ID);
      assertThat(nativeEncryptionService.decrypt(awsSecret.getAwsSourceProfileSecretAccessKey()))
          .isEqualTo(AWS_SOURCE_PROFILE_SECRET_ACCESS_KEY);
    }

    @Test
    @DisplayName("Given a request, should never store the sensitive values in clear text")
    void given_request_should_notStoreSensitiveValuesInClearText() {
      // Arrange
      SecretStoreRequest request = awsAssumeRoleStaticAccessKeyRequest();

      // Act
      AwsAssumeRoleSecret awsSecret = (AwsAssumeRoleSecret) handler.buildOrUpdate(null, request);

      // Assert
      assertThat(awsSecret.getAwsExternalId()).isNotEqualTo(AWS_EXTERNAL_ID);
      assertThat(awsSecret.getAwsSourceProfileSecretAccessKey())
          .isNotEqualTo(AWS_SOURCE_PROFILE_SECRET_ACCESS_KEY);
      // the access key id is an identifier, not a secret: it stays readable
      assertThat(awsSecret.getAwsSourceProfileAccessKeyId())
          .isEqualTo(AWS_SOURCE_PROFILE_ACCESS_KEY_ID);
    }

    @Test
    @DisplayName("Given a request without external id, should build a secret without external id")
    void given_requestWithoutExternalId_should_buildSecretWithoutExternalId() {
      // Arrange
      SecretStoreRequest request =
          awsAssumeRoleRequest(
              AWS_DEFAULT_REGION, AWS_ROLE_ARN, null, INSTANCE_DEFAULT, null, null);

      // Act
      AwsAssumeRoleSecret awsSecret = (AwsAssumeRoleSecret) handler.buildOrUpdate(null, request);

      // Assert
      assertThat(awsSecret.getAwsExternalId()).isNull();
    }

    @Test
    @DisplayName("Given an existing secret of another type, should build a brand new secret")
    void given_existingSecretOfAnotherType_should_buildNewSecret() {
      // Arrange
      AwsAccessKeySecret existingSecret = new AwsAccessKeySecret();
      existingSecret.setAwsAccessKeyId("should-be-ignored");

      // Act
      Secret secret = handler.buildOrUpdate(existingSecret, awsAssumeRoleInstanceDefaultRequest());

      // Assert
      assertThat(secret).isInstanceOf(AwsAssumeRoleSecret.class).isNotSameAs(existingSecret);
    }
  }

  @Nested
  @DisplayName("buildOrUpdate - update")
  class BuildOrUpdateUpdate {

    @Test
    @DisplayName("Given an existing secret, should update it in place")
    void given_existingSecret_should_updateItInPlace() {
      // Arrange
      AwsAssumeRoleSecret existingSecret = instanceDefaultSecret();
      SecretStoreRequest request =
          awsAssumeRoleRequest(AWS_OTHER_REGION, AWS_OTHER_ROLE_ARN, null, null, null, null);

      // Act
      Secret secret = handler.buildOrUpdate(existingSecret, request);

      // Assert
      assertThat(secret).isSameAs(existingSecret);
      assertThat(existingSecret.getAwsDefaultRegion()).isEqualTo(AWS_OTHER_REGION);
      assertThat(existingSecret.getAwsRoleArn()).isEqualTo(AWS_OTHER_ROLE_ARN);
      assertThat(existingSecret.getAwsSourceIdentityType()).isEqualTo(INSTANCE_DEFAULT);
    }

    @Test
    @DisplayName("Given an empty request on a complete secret, should leave it untouched")
    void given_emptyRequestOnCompleteSecret_should_leaveItUntouched() {
      // Arrange
      AwsAssumeRoleSecret existingSecret = staticAccessKeySecret();
      String encryptedExternalId = existingSecret.getAwsExternalId();
      String encryptedSourceSecret = existingSecret.getAwsSourceProfileSecretAccessKey();

      // Act
      AwsAssumeRoleSecret awsSecret =
          (AwsAssumeRoleSecret) handler.buildOrUpdate(existingSecret, emptyRequest());

      // Assert
      assertThat(awsSecret.getAwsDefaultRegion()).isEqualTo(AWS_DEFAULT_REGION);
      assertThat(awsSecret.getAwsRoleArn()).isEqualTo(AWS_ROLE_ARN);
      assertThat(awsSecret.getAwsSourceIdentityType()).isEqualTo(STATIC_ACCESS_KEY);
      assertThat(awsSecret.getAwsExternalId()).isEqualTo(encryptedExternalId);
      assertThat(awsSecret.getAwsSourceProfileSecretAccessKey()).isEqualTo(encryptedSourceSecret);
    }

    @Test
    @DisplayName("Given a new external id, should re-encrypt it with a new cipher text")
    void given_newExternalId_should_reEncryptIt() {
      // Arrange
      AwsAssumeRoleSecret existingSecret = instanceDefaultSecret();
      String previousCipherText = existingSecret.getAwsExternalId();
      SecretStoreRequest request =
          awsAssumeRoleRequest(null, null, "another-external-id", null, null, null);

      // Act
      AwsAssumeRoleSecret awsSecret =
          (AwsAssumeRoleSecret) handler.buildOrUpdate(existingSecret, request);

      // Assert
      assertThat(awsSecret.getAwsExternalId()).isNotEqualTo(previousCipherText);
      assertThat(nativeEncryptionService.decrypt(awsSecret.getAwsExternalId()))
          .isEqualTo("another-external-id");
    }

    @Test
    @DisplayName("Given a new source profile secret, should re-encrypt it")
    void given_newSourceProfileSecret_should_reEncryptIt() {
      // Arrange
      AwsAssumeRoleSecret existingSecret = staticAccessKeySecret();
      String previousCipherText = existingSecret.getAwsSourceProfileSecretAccessKey();
      SecretStoreRequest request =
          awsAssumeRoleRequest(null, null, null, null, null, AWS_OTHER_SECRET_ACCESS_KEY);

      // Act
      AwsAssumeRoleSecret awsSecret =
          (AwsAssumeRoleSecret) handler.buildOrUpdate(existingSecret, request);

      // Assert
      assertThat(awsSecret.getAwsSourceProfileSecretAccessKey()).isNotEqualTo(previousCipherText);
      assertThat(nativeEncryptionService.decrypt(awsSecret.getAwsSourceProfileSecretAccessKey()))
          .isEqualTo(AWS_OTHER_SECRET_ACCESS_KEY);
    }
  }

  @Nested
  @DisplayName("buildOrUpdate - source identity type switch")
  class BuildOrUpdateSourceIdentityTypeSwitch {

    @Test
    @DisplayName("Given a static access key type resent with new keys, should replace the keys")
    void given_staticAccessKeyTypeResentWithNewKeys_should_replaceKeys() {
      // Arrange
      AwsAssumeRoleSecret existingSecret = staticAccessKeySecret();
      SecretStoreRequest request =
          awsAssumeRoleRequest(
              null,
              null,
              null,
              STATIC_ACCESS_KEY,
              "AKIAIOSFODNN7ROTATED",
              AWS_OTHER_SECRET_ACCESS_KEY);

      // Act
      AwsAssumeRoleSecret awsSecret =
          (AwsAssumeRoleSecret) handler.buildOrUpdate(existingSecret, request);

      // Assert
      assertThat(awsSecret.getAwsSourceProfileAccessKeyId()).isEqualTo("AKIAIOSFODNN7ROTATED");
      assertThat(nativeEncryptionService.decrypt(awsSecret.getAwsSourceProfileSecretAccessKey()))
          .isEqualTo(AWS_OTHER_SECRET_ACCESS_KEY);
    }

    @Test
    @DisplayName("Given a static access key type resent without keys, should throw")
    void given_staticAccessKeyTypeResentWithoutKeys_should_throw() {
      // Arrange — resending STATIC_ACCESS_KEY clears the stored source profile, so the caller
      // must always resend both the access key id and its secret
      AwsAssumeRoleSecret existingSecret = staticAccessKeySecret();
      SecretStoreRequest request =
          awsAssumeRoleRequest(null, null, null, STATIC_ACCESS_KEY, null, null);

      // Act & Assert
      assertThatThrownBy(() -> handler.buildOrUpdate(existingSecret, request))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(
              "source profile access key id and secret access key are required for STATIC_ACCESS_KEY");
    }

    @Test
    @DisplayName(
        "Given a switch from static access key to instance default, should not clear the stored"
            + " source profile (current behaviour)")
    void given_switchToInstanceDefault_should_notClearStoredSourceProfile() {
      // Arrange
      AwsAssumeRoleSecret existingSecret = staticAccessKeySecret();
      SecretStoreRequest request =
          awsAssumeRoleRequest(null, null, null, INSTANCE_DEFAULT, null, null);

      // Act
      AwsAssumeRoleSecret awsSecret =
          (AwsAssumeRoleSecret) handler.buildOrUpdate(existingSecret, request);

      // Assert — the handler only resets the source profile when the type is STATIC_ACCESS_KEY,
      // so switching to INSTANCE_DEFAULT keeps the previous (now unused) credentials
      assertThat(awsSecret.getAwsSourceIdentityType()).isEqualTo(INSTANCE_DEFAULT);
      assertThat(awsSecret.getAwsSourceProfileAccessKeyId())
          .isEqualTo(AWS_SOURCE_PROFILE_ACCESS_KEY_ID);
      assertThat(awsSecret.getAwsSourceProfileSecretAccessKey()).isNotNull();
    }

    @Test
    @DisplayName("Given a switch from instance default to static access key with keys, should pass")
    void given_switchToStaticAccessKeyWithKeys_should_pass() {
      // Arrange
      AwsAssumeRoleSecret existingSecret = instanceDefaultSecret();
      SecretStoreRequest request =
          awsAssumeRoleRequest(
              null,
              null,
              null,
              STATIC_ACCESS_KEY,
              AWS_SOURCE_PROFILE_ACCESS_KEY_ID,
              AWS_SOURCE_PROFILE_SECRET_ACCESS_KEY);

      // Act
      AwsAssumeRoleSecret awsSecret =
          (AwsAssumeRoleSecret) handler.buildOrUpdate(existingSecret, request);

      // Assert
      assertThat(awsSecret.getAwsSourceIdentityType()).isEqualTo(STATIC_ACCESS_KEY);
      assertThat(awsSecret.getAwsSourceProfileAccessKeyId())
          .isEqualTo(AWS_SOURCE_PROFILE_ACCESS_KEY_ID);
      assertThat(nativeEncryptionService.decrypt(awsSecret.getAwsSourceProfileSecretAccessKey()))
          .isEqualTo(AWS_SOURCE_PROFILE_SECRET_ACCESS_KEY);
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
          awsAssumeRoleRequest(null, AWS_ROLE_ARN, AWS_EXTERNAL_ID, INSTANCE_DEFAULT, null, null);

      // Act & Assert
      assertThatThrownBy(() -> handler.buildOrUpdate(null, request))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(
              "AWS default region, role ARN and source identity type are required");
    }

    @Test
    @DisplayName("Given a request without role ARN, should throw")
    void given_requestWithoutRoleArn_should_throw() {
      // Arrange
      SecretStoreRequest request =
          awsAssumeRoleRequest(
              AWS_DEFAULT_REGION, null, AWS_EXTERNAL_ID, INSTANCE_DEFAULT, null, null);

      // Act & Assert
      assertThatThrownBy(() -> handler.buildOrUpdate(null, request))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(
              "AWS default region, role ARN and source identity type are required");
    }

    @Test
    @DisplayName("Given a request without source identity type, should throw")
    void given_requestWithoutSourceIdentityType_should_throw() {
      // Arrange
      SecretStoreRequest request =
          awsAssumeRoleRequest(AWS_DEFAULT_REGION, AWS_ROLE_ARN, AWS_EXTERNAL_ID, null, null, null);

      // Act & Assert
      assertThatThrownBy(() -> handler.buildOrUpdate(null, request))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(
              "AWS default region, role ARN and source identity type are required");
    }

    @Test
    @DisplayName(
        "Given a static access key request without source profile access key id, should throw")
    void given_staticAccessKeyRequestWithoutAccessKeyId_should_throw() {
      // Arrange
      SecretStoreRequest request =
          awsAssumeRoleRequest(
              AWS_DEFAULT_REGION,
              AWS_ROLE_ARN,
              AWS_EXTERNAL_ID,
              STATIC_ACCESS_KEY,
              null,
              AWS_SOURCE_PROFILE_SECRET_ACCESS_KEY);

      // Act & Assert
      assertThatThrownBy(() -> handler.buildOrUpdate(null, request))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(
              "source profile access key id and secret access key are required for STATIC_ACCESS_KEY");
    }

    @Test
    @DisplayName("Given a static access key request without source profile secret, should throw")
    void given_staticAccessKeyRequestWithoutSecret_should_throw() {
      // Arrange
      SecretStoreRequest request =
          awsAssumeRoleRequest(
              AWS_DEFAULT_REGION,
              AWS_ROLE_ARN,
              AWS_EXTERNAL_ID,
              STATIC_ACCESS_KEY,
              AWS_SOURCE_PROFILE_ACCESS_KEY_ID,
              null);

      // Act & Assert
      assertThatThrownBy(() -> handler.buildOrUpdate(null, request))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(
              "source profile access key id and secret access key are required for STATIC_ACCESS_KEY");
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
    void given_accessKeyRequest_should_throw() {
      // Arrange
      SecretStoreRequest request = awsAccessKeyRequest();

      // Act & Assert
      assertThatThrownBy(() -> handler.buildOrUpdate(null, request))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(
              "AWS default region, role ARN and source identity type are required");
    }
  }

  @Nested
  @DisplayName("toMetadata")
  class ToMetadata {

    @Test
    @DisplayName("Given a static access key secret, should expose the non sensitive fields")
    void given_staticAccessKeySecret_should_exposeNonSensitiveFields() {
      // Arrange
      AwsAssumeRoleSecret secret = staticAccessKeySecret();

      // Act
      SecretMetadata metadata = handler.toMetadata(secret);

      // Assert
      assertThat(metadata.awsDefaultRegion()).isEqualTo(AWS_DEFAULT_REGION);
      assertThat(metadata.awsRoleArn()).isEqualTo(AWS_ROLE_ARN);
      assertThat(metadata.awsSourceIdentityType()).isEqualTo(STATIC_ACCESS_KEY);
      assertThat(metadata.awsSourceProfileAccessKeyId())
          .isEqualTo(AWS_SOURCE_PROFILE_ACCESS_KEY_ID);
      assertThat(metadata.username()).isNull();
      assertThat(metadata.hashAlgorithm()).isNull();
      assertThat(metadata.awsAccessKeyId()).isNull();
    }

    @Test
    @DisplayName("Given an instance default secret, should expose a null source profile")
    void given_instanceDefaultSecret_should_exposeNullSourceProfile() {
      // Arrange
      AwsAssumeRoleSecret secret = instanceDefaultSecret();

      // Act
      SecretMetadata metadata = handler.toMetadata(secret);

      // Assert
      assertThat(metadata.awsSourceIdentityType()).isEqualTo(INSTANCE_DEFAULT);
      assertThat(metadata.awsSourceProfileAccessKeyId()).isNull();
    }

    @Test
    @DisplayName("Given an assume role secret, should never expose any secret value")
    void given_assumeRoleSecret_should_notExposeSecretValues() {
      // Arrange
      AwsAssumeRoleSecret secret = staticAccessKeySecret();

      // Act
      SecretMetadata metadata = handler.toMetadata(secret);

      // Assert
      assertThat(metadata.toString())
          .doesNotContain(AWS_EXTERNAL_ID)
          .doesNotContain(AWS_SOURCE_PROFILE_SECRET_ACCESS_KEY)
          .doesNotContain(secret.getAwsExternalId())
          .doesNotContain(secret.getAwsSourceProfileSecretAccessKey());
    }

    @Test
    @DisplayName("Given a secret of another type, should throw")
    void given_secretOfAnotherType_should_throw() {
      // Arrange
      Secret secret = new AwsAccessKeySecret();

      // Act & Assert
      assertThatThrownBy(() -> handler.toMetadata(secret))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Secret type mismatch: expected AWS_ASSUME_ROLE secret");
    }

    @Test
    @DisplayName("Given a null secret, should throw")
    void given_nullSecret_should_throw() {
      // Arrange & Act & Assert
      assertThatThrownBy(() -> handler.toMetadata(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Secret type mismatch: expected AWS_ASSUME_ROLE secret");
    }
  }
}
