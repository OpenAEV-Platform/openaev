package io.openaev.secrets.provider.impl.handlers;

import io.openaev.database.model.AwsAccessKeySecret;
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
public class AwsAccessKeyHandler implements SecretHandler {

  protected final NativeEncryptionService nativeEncryptionService;
  private final AwsCredentialConnectivityCheck awsCredentialConnectivityCheck;

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
      if (request.awsSessionToken().isBlank()) {
        awsSecret.setAwsSessionToken(null);
      } else {
        awsSecret.setAwsSessionToken(
            nativeEncryptionService.encrypt(
                Objects.requireNonNull(
                    request.awsSessionToken(), "request.awsSessionToken must not be null")));
      }
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
          awsAccessKeySecret.getAwsDefaultRegion(),
          awsAccessKeySecret.getAwsAccessKeyId(),
          awsAccessKeySecret.getAwsSessionToken() != null
              && !awsAccessKeySecret.getAwsSessionToken().isBlank());
    }
    throw new IllegalArgumentException(
        "Secret type mismatch: expected "
            + CredentialSecretReference.CREDENTIAL_AUTH_METHOD.AWS_ACCESS_KEY
            + " secret");
  }

  @Override
  public SecretConnectionResult validateConnection(Secret secret) {
    if (!(secret instanceof AwsAccessKeySecret awsAccessKeySecret)) {
      throw new IllegalArgumentException(
          "Secret type mismatch: expected "
              + CredentialSecretReference.CREDENTIAL_AUTH_METHOD.AWS_ACCESS_KEY
              + " secret");
    }
    String sessionToken = null;
    if (awsAccessKeySecret.getAwsSessionToken() != null
        && !awsAccessKeySecret.getAwsSessionToken().isBlank()) {
      sessionToken = nativeEncryptionService.decrypt(awsAccessKeySecret.getAwsSessionToken());
    }
    return awsCredentialConnectivityCheck.validateAccessKey(
        awsAccessKeySecret.getAwsDefaultRegion(),
        awsAccessKeySecret.getAwsAccessKeyId(),
        nativeEncryptionService.decrypt(awsAccessKeySecret.getAwsSecretAccessKey()),
        sessionToken);
  }
}
