package io.openaev.secrets.provider.impl.handlers;

import io.openaev.database.model.CredentialSecretReference;
import io.openaev.database.model.GcpScopes;
import io.openaev.database.model.GcpServiceAccountSecret;
import io.openaev.database.model.Secret;
import io.openaev.database.model.SecretReference;
import io.openaev.secrets.provider.SecretMetadata;
import io.openaev.secrets.provider.SecretStoreRequest;
import io.openaev.service.connector_instances.NativeEncryptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Stores GCP service account credentials: an uploaded JSON key file plus its scope and project.
 *
 * <p>Spring discovers this handler through {@code SecretHandlerResolver}, which injects {@code
 * List<SecretHandler>}: declaring the bean is enough to wire it into the secrets provider and, once
 * {@code validateConnection} lands, into the background validation job.
 *
 * <p>The key file never reaches this class as a {@code MultipartFile}: the API layer validates the
 * upload and hands down raw bytes, so the handler stays free of any transport concern.
 */
@Component
@RequiredArgsConstructor
public class GcpServiceAccountHandler implements SecretHandler {

  static final String MANDATORY_FIELDS_MESSAGE =
      "GCP scope and service account key file are required";
  static final String TYPE_MISMATCH_MESSAGE =
      "Secret type mismatch: expected GCP_SERVICE_ACCOUNT secret";

  protected final NativeEncryptionService nativeEncryptionService;

  @Override
  public boolean supports(Secret secret) {
    return secret instanceof GcpServiceAccountSecret;
  }

  @Override
  public boolean supports(SecretReference reference) {
    return reference instanceof CredentialSecretReference credential
        && credential.getCredentialAuthMethod()
            == CredentialSecretReference.CREDENTIAL_AUTH_METHOD.GCP_SERVICE_ACCOUNT;
  }

  @Override
  public Secret buildOrUpdate(Secret existingSecret, SecretStoreRequest request) {
    boolean creating = !(existingSecret instanceof GcpServiceAccountSecret);
    GcpServiceAccountSecret gcpSecret =
        creating ? new GcpServiceAccountSecret() : (GcpServiceAccountSecret) existingSecret;

    // A null value means "left untouched by the client", so the stored value must be kept: the
    // form strips unchanged write-only fields from the payload, and the key file part is simply
    // absent when the user does not rotate it.
    if (request.gcpScope() != null) {
      gcpSecret.setScope(request.gcpScope());
    } else if (creating) {
      gcpSecret.setScope(GcpScopes.DEFAULT_CLOUD_PLATFORM);
    }

    if (request.gcpProjectId() != null) {
      gcpSecret.setProjectId(request.gcpProjectId());
    }

    if (request.gcpPrivateKeyJson() != null) {
      gcpSecret.setPrivateKeyJson(nativeEncryptionService.encrypt(request.gcpPrivateKeyJson()));
    }

    // The FINAL state of the entity is validated, not the incoming request: an update that does
    // not resend the key file is legitimate, and checking the request would make key rotation the
    // only way to ever edit a GCP credential.
    if (gcpSecret.getScope() == null || gcpSecret.getPrivateKeyJson() == null) {
      throw new IllegalArgumentException(MANDATORY_FIELDS_MESSAGE);
    }

    return gcpSecret;
  }

  /**
   * Exposes only what the form needs to prefill itself.
   *
   * <p>No decryption happens here: the key file is reduced to a boolean, so neither the plaintext
   * nor the cipher text can travel back through {@code CredentialFullOutput}.
   */
  @Override
  public SecretMetadata toMetadata(Secret secret) {
    if (secret instanceof GcpServiceAccountSecret gcpSecret) {
      return SecretMetadata.forGcpServiceAccount(
          gcpSecret.getScope(), gcpSecret.getProjectId(), gcpSecret.getPrivateKeyJson() != null);
    }
    throw new IllegalArgumentException(TYPE_MISMATCH_MESSAGE);
  }
}
