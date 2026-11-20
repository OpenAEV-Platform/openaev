package io.openaev.secrets.provider.impl.handlers;

import io.openaev.database.model.AwsAccessKeySecret;
import io.openaev.database.model.CredentialSecretReference;
import io.openaev.database.model.Secret;
import io.openaev.database.model.SecretReference;
import io.openaev.secrets.provider.SecretConnectionResult;
import io.openaev.secrets.provider.SecretMetadata;
import io.openaev.secrets.provider.SecretResolvedValue;
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
    AwsAccessKeySecret awsSecret;
    if (existingSecret instanceof AwsAccessKeySecret casted) {
      refuseEndpointChangeWithoutNewSecretValues(casted, request);
      awsSecret = casted;
    } else {
      awsSecret = new AwsAccessKeySecret();
    }

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

    if (request.awsAccountId() != null) {
      awsSecret.setAwsAccountId(SecretHandler.blankToNull(request.awsAccountId()));
    }

    if (request.awsEndpointUrl() != null) {
      awsSecret.setAwsEndpointUrl(SecretHandler.blankToNull(request.awsEndpointUrl()));
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
              && !awsAccessKeySecret.getAwsSessionToken().isBlank(),
          awsAccessKeySecret.getAwsAccountId(),
          awsAccessKeySecret.getAwsEndpointUrl());
    }
    throw new IllegalArgumentException(
        "Secret type mismatch: expected "
            + CredentialSecretReference.CREDENTIAL_AUTH_METHOD.AWS_ACCESS_KEY
            + " secret");
  }

  @Override
  public SecretResolvedValue toResolvedValue(Secret secret) {
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
    return SecretResolvedValue.forAwsAccessKey(
        awsAccessKeySecret.getAwsDefaultRegion(),
        awsAccessKeySecret.getAwsAccessKeyId(),
        nativeEncryptionService.decrypt(awsAccessKeySecret.getAwsSecretAccessKey()),
        sessionToken,
        awsAccessKeySecret.getAwsAccountId(),
        awsAccessKeySecret.getAwsEndpointUrl());
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
        sessionToken,
        awsAccessKeySecret.getAwsEndpointUrl());
  }

  /**
   * The stored secrets must not be replayed against a new endpoint: the caller has to supply them
   * again, otherwise whoever can edit the credential could redirect them to a host they control.
   */
  private static void refuseEndpointChangeWithoutNewSecretValues(
      AwsAccessKeySecret existing, SecretStoreRequest request) {
    if (!SecretHandler.redirectsEndpoint(existing.getAwsEndpointUrl(), request.awsEndpointUrl())) {
      return;
    }
    boolean secretKeyMissing = request.awsSecretAccessKey() == null;
    boolean sessionTokenMissing =
        existing.getAwsSessionToken() != null
            && !existing.getAwsSessionToken().isBlank()
            && request.awsSessionToken() == null;
    if (secretKeyMissing || sessionTokenMissing) {
      throw new IllegalArgumentException(
          "AWS secret access key and session token (if set) must be reset in order to change the endpoint URL");
    }
  }
}
