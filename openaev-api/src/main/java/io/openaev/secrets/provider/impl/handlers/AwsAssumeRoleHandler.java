package io.openaev.secrets.provider.impl.handlers;

import static io.openaev.database.model.AwsAssumeRoleSecret.AWS_SOURCE_IDENTITY_TYPE.STATIC_ACCESS_KEY;

import io.openaev.database.model.AwsAssumeRoleSecret;
import io.openaev.database.model.CredentialSecretReference;
import io.openaev.database.model.Secret;
import io.openaev.database.model.SecretReference;
import io.openaev.secrets.provider.SecretConnectionResult;
import io.openaev.secrets.provider.SecretMetadata;
import io.openaev.secrets.provider.SecretStoreRequest;
import io.openaev.secrets.provider.impl.validators.AwsCredentialConnectivityCheck;
import io.openaev.service.connector_instances.NativeEncryptionService;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AwsAssumeRoleHandler implements SecretHandler {

  protected final NativeEncryptionService nativeEncryptionService;
  private final AwsCredentialConnectivityCheck awsCredentialConnectivityCheck;

  @Override
  public boolean supports(Secret secret) {
    return secret instanceof AwsAssumeRoleSecret;
  }

  @Override
  public boolean supports(SecretReference reference) {
    return reference instanceof CredentialSecretReference credential
        && credential.getCredentialAuthMethod()
            == CredentialSecretReference.CREDENTIAL_AUTH_METHOD.AWS_ASSUME_ROLE;
  }

  @Override
  public Secret buildOrUpdate(Secret existingSecret, SecretStoreRequest request) {
    AwsAssumeRoleSecret awsSecret =
        existingSecret instanceof AwsAssumeRoleSecret casted ? casted : new AwsAssumeRoleSecret();

    if (request.awsDefaultRegion() != null) {
      awsSecret.setAwsDefaultRegion(request.awsDefaultRegion());
    }

    if (request.awsRoleArn() != null) {
      awsSecret.setAwsRoleArn(request.awsRoleArn());
    }

    if (request.awsExternalId() != null) {
      awsSecret.setAwsExternalId(
          nativeEncryptionService.encrypt(
              Objects.requireNonNull(
                  request.awsExternalId(), "request.awsExternalId must not be null")));
    }

    if (request.awsSourceIdentityType() != null) {
      awsSecret.setAwsSourceIdentityType(request.awsSourceIdentityType());
      if (STATIC_ACCESS_KEY.equals(request.awsSourceIdentityType())) {
        awsSecret.setAwsSourceProfileAccessKeyId(null);
        awsSecret.setAwsSourceProfileSecretAccessKey(null);
      }
    }

    if (request.awsSourceProfileAccessKeyId() != null) {
      awsSecret.setAwsSourceProfileAccessKeyId(request.awsSourceProfileAccessKeyId());
    }

    if (request.awsSourceProfileSecretAccessKey() != null) {
      awsSecret.setAwsSourceProfileSecretAccessKey(
          nativeEncryptionService.encrypt(
              Objects.requireNonNull(
                  request.awsSourceProfileSecretAccessKey(),
                  "request.awsSourceProfileSecretAccessKey must not be null")));
    }

    if (awsSecret.getAwsDefaultRegion() == null
        || awsSecret.getAwsRoleArn() == null
        || awsSecret.getAwsSourceIdentityType() == null) {
      throw new IllegalArgumentException(
          "AWS default region, role ARN and source identity type are required");
    }

    if (awsSecret.getAwsSourceIdentityType() == STATIC_ACCESS_KEY
        && (awsSecret.getAwsSourceProfileAccessKeyId() == null
            || awsSecret.getAwsSourceProfileSecretAccessKey() == null)) {
      throw new IllegalArgumentException(
          "source profile access key id and secret access key are required for STATIC_ACCESS_KEY");
    }

    return awsSecret;
  }

  @Override
  public SecretMetadata toMetadata(Secret secret) {
    if (secret instanceof AwsAssumeRoleSecret awsAssumeRoleSecret) {
      return SecretMetadata.forAwsAssumeRole(
          awsAssumeRoleSecret.getAwsDefaultRegion(),
          awsAssumeRoleSecret.getAwsRoleArn(),
          awsAssumeRoleSecret.getAwsSourceIdentityType(),
          awsAssumeRoleSecret.getAwsSourceProfileAccessKeyId());
    }
    throw new IllegalArgumentException("Secret type mismatch: expected AWS_ASSUME_ROLE secret");
  }

  @Override
  public SecretConnectionResult validateConnection(Secret secret) {
    if (!(secret instanceof AwsAssumeRoleSecret awsAssumeRoleSecret)) {
      throw new IllegalArgumentException("Secret type mismatch: expected AWS_ASSUME_ROLE secret");
    }
    String externalId = null;
    if (awsAssumeRoleSecret.getAwsExternalId() != null) {
      externalId = nativeEncryptionService.decrypt(awsAssumeRoleSecret.getAwsExternalId());
    }

    String sourceProfileSecretAccessKey = null;
    if (STATIC_ACCESS_KEY.equals(awsAssumeRoleSecret.getAwsSourceIdentityType())) {
      sourceProfileSecretAccessKey =
          nativeEncryptionService.decrypt(awsAssumeRoleSecret.getAwsSourceProfileSecretAccessKey());
    }

    return awsCredentialConnectivityCheck.validateAssumeRole(
        awsAssumeRoleSecret.getAwsDefaultRegion(),
        awsAssumeRoleSecret.getAwsRoleArn(),
        externalId,
        awsAssumeRoleSecret.getAwsSourceIdentityType(),
        awsAssumeRoleSecret.getAwsSourceProfileAccessKeyId(),
        sourceProfileSecretAccessKey);
  }
}
