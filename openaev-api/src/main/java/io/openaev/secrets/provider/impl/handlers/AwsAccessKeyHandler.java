package io.openaev.secrets.provider.impl.handlers;

import io.openaev.database.model.AwsAccessKeySecret;
import io.openaev.database.model.CredentialSecretReference;
import io.openaev.database.model.Secret;
import io.openaev.database.model.SecretReference;
import io.openaev.secrets.provider.SecretMetadata;
import io.openaev.secrets.provider.SecretStoreRequest;
import io.openaev.service.connector_instances.NativeEncryptionService;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AwsAccessKeyHandler implements SecretHandler {

  protected final NativeEncryptionService nativeEncryptionService;

  @Override
  public boolean supports(Secret secret) {
    return secret instanceof AwsAccessKeySecret;
  }

  @Override
  public boolean supports(SecretReference reference) {
    return reference instanceof CredentialSecretReference credential
        && credential.getCredentialAuthMethod()
            == CredentialSecretReference.CREDENTIAL_AUTH_METHOD.AWS_ACCESS_KEY;
  }

  @Override
  public Secret buildOrUpdate(Secret existingSecret, SecretStoreRequest request) {
    AwsAccessKeySecret awsSecret =
        existingSecret instanceof AwsAccessKeySecret casted ? casted : new AwsAccessKeySecret();

    if (request.awsDefaultRegion() != null) {
      awsSecret.setAwsDefaultRegion(request.awsDefaultRegion());
    }

    if (request.awsAccessKeyId() != null) {
      awsSecret.setAwsAccessKeyId(request.awsAccessKeyId());
    }

    if (request.awsSecretAccessKey() != null) {
      awsSecret.setAwsSecretAccessKey(
          nativeEncryptionService.encrypt(
              Objects.requireNonNull(
                  request.awsSecretAccessKey(), "request.awsSecretAccessKey must not be null")));
    }

    if (request.awsSessionToken() != null) {
      awsSecret.setAwsSessionToken(
          nativeEncryptionService.encrypt(
              Objects.requireNonNull(
                  request.awsSessionToken(), "request.awsSessionToken must not be null")));
    }

    if (awsSecret.getAwsDefaultRegion() == null
        || awsSecret.getAwsAccessKeyId() == null
        || awsSecret.getAwsSecretAccessKey() == null) {
      throw new IllegalArgumentException(
          "AWS default region, access key id and secret are required");
    }

    return awsSecret;
  }

  @Override
  public SecretMetadata toMetadata(Secret secret) {
    if (secret instanceof AwsAccessKeySecret awsAccessKeySecret) {
      return SecretMetadata.forAwsAccessKey(
          awsAccessKeySecret.getAwsDefaultRegion(), awsAccessKeySecret.getAwsAccessKeyId());
    }
    throw new IllegalArgumentException("Secret type mismatch: expected AWS_ACCESS_KEY secret");
  }
}
