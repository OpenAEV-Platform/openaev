package io.openaev.secrets.provider.impl.handlers;

import io.openaev.database.model.CredentialSecretReference;
import io.openaev.database.model.GcpOAuth2Secret;
import io.openaev.database.model.GcpScopes;
import io.openaev.database.model.Secret;
import io.openaev.database.model.SecretReference;
import io.openaev.secrets.provider.SecretConnectionResult;
import io.openaev.secrets.provider.SecretMetadata;
import io.openaev.secrets.provider.SecretStoreRequest;
import io.openaev.secrets.provider.impl.validators.GcpCredentialConnectivityCheck;
import io.openaev.service.connector_instances.NativeEncryptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Stores GCP OAuth 2.0 credentials: a client id/secret pair plus the refresh token obtained once
 * from the user's consent, and the scope and project they apply to.
 *
 * <p>Spring discovers this handler through {@code SecretHandlerResolver}, which injects {@code
 * List<SecretHandler>}: declaring the bean is enough to wire it into the secrets provider and into
 * the background validation job at once.
 *
 * <p>Everything travels as plain text fields of the {@code input} part — no file is involved here,
 * unlike {@code GcpServiceAccountHandler}.
 */
@Component
@RequiredArgsConstructor
public class GcpOAuth2Handler implements SecretHandler {

  static final String MANDATORY_FIELDS_MESSAGE =
      "GCP scope, OAuth client id, client secret and refresh token are required";
  static final String TYPE_MISMATCH_MESSAGE = "Secret type mismatch: expected GCP_OAUTH2 secret";

  protected final NativeEncryptionService nativeEncryptionService;
  private final GcpCredentialConnectivityCheck gcpCredentialConnectivityCheck;

  @Override
  public boolean supports(Secret secret) {
    return secret instanceof GcpOAuth2Secret;
  }

  @Override
  public boolean supports(SecretReference reference) {
    return reference instanceof CredentialSecretReference credential
        && credential.getCredentialAuthMethod()
            == CredentialSecretReference.CREDENTIAL_AUTH_METHOD.GCP_OAUTH2;
  }

  @Override
  public Secret buildOrUpdate(Secret existingSecret, SecretStoreRequest request) {
    boolean creating = !(existingSecret instanceof GcpOAuth2Secret);
    GcpOAuth2Secret gcpSecret = creating ? new GcpOAuth2Secret() : (GcpOAuth2Secret) existingSecret;

    // A null value means "left untouched by the client", so the stored value must be kept: the
    // form strips unchanged write-only fields from the payload.
    if (request.gcpScope() != null) {
      gcpSecret.setScope(request.gcpScope());
    } else if (creating) {
      gcpSecret.setScope(GcpScopes.DEFAULT_CLOUD_PLATFORM);
    }

    if (request.gcpProjectId() != null) {
      gcpSecret.setProjectId(request.gcpProjectId());
    }

    // The client id is a public application identifier, stored in clear text like azure_client_id.
    if (request.gcpOauthClientId() != null) {
      gcpSecret.setOauthClientId(request.gcpOauthClientId());
    }

    if (request.gcpOauthClientSecret() != null) {
      gcpSecret.setOauthClientSecret(
          nativeEncryptionService.encrypt(request.gcpOauthClientSecret()));
    }

    if (request.gcpOauthRefreshToken() != null) {
      gcpSecret.setOauthRefreshToken(
          nativeEncryptionService.encrypt(request.gcpOauthRefreshToken()));
    }

    // The FINAL state of the entity is validated, not the incoming request: a PUT that does not
    // resend the write-only fields is legitimate, and checking the request would make client
    // secret and refresh token rotation the only way to ever edit a GCP OAuth2 credential.
    if (gcpSecret.getOauthClientId() == null
        || gcpSecret.getOauthClientSecret() == null
        || gcpSecret.getOauthRefreshToken() == null
        || gcpSecret.getScope() == null) {
      throw new IllegalArgumentException(MANDATORY_FIELDS_MESSAGE);
    }

    return gcpSecret;
  }

  /**
   * Exposes only what the form needs to prefill itself.
   *
   * <p>No decryption happens here: the client secret and the refresh token are reduced to booleans,
   * so neither the plaintext nor the cipher text can travel back through {@code
   * CredentialFullOutput}.
   */
  @Override
  public SecretMetadata toMetadata(Secret secret) {
    if (secret instanceof GcpOAuth2Secret gcpSecret) {
      return SecretMetadata.forGcpOAuth2(
          gcpSecret.getScope(),
          gcpSecret.getProjectId(),
          gcpSecret.getOauthClientId(),
          gcpSecret.getOauthClientSecret() != null,
          gcpSecret.getOauthRefreshToken() != null);
    }
    throw new IllegalArgumentException(TYPE_MISMATCH_MESSAGE);
  }

  /**
   * Decrypts the stored client secret and refresh token and probes Google with them.
   *
   * <p>The plaintexts are built here and handed straight to the validator: they are never logged,
   * never stored back on the entity, and never travel back in the result.
   *
   * <p>No lazy association is touched: this runs in the validation job's phase 2, outside any
   * transaction and outside any tenant scope, so only already-loaded basic columns may be read.
   */
  @Override
  public SecretConnectionResult validateConnection(Secret secret) {
    if (!(secret instanceof GcpOAuth2Secret gcpSecret)) {
      throw new IllegalArgumentException(TYPE_MISMATCH_MESSAGE);
    }
    return gcpCredentialConnectivityCheck.validateOAuth2(
        gcpSecret.getOauthClientId(),
        nativeEncryptionService.decrypt(gcpSecret.getOauthClientSecret()),
        nativeEncryptionService.decrypt(gcpSecret.getOauthRefreshToken()),
        gcpSecret.getScope(),
        gcpSecret.getProjectId());
  }
}
