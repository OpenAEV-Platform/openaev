package io.openaev.secrets.provider;

import io.openaev.database.model.SecretReference;

/**
 * Contract for secret storage backends. Each implementation handles a specific storage mechanism
 * (local DB, HashiCorp Vault, CyberArk, etc.).
 */
public interface SecretProvider {

  /**
   * Store a secret value and persist the associated {@link SecretReference}
   *
   * @param secretReference the pre-built reference (name, type, authMethod, tenant already set)
   * @param request typed secret payload to encrypt before persistence
   * @return the secretReference persisted
   */
  SecretReference store(SecretReference secretReference, SecretStoreRequest request);

  /**
   * Update a secret and update the associated SecretReference.
   *
   * @param secretReference the reference to update
   * @param request typed secret payload to update
   * @return the updated secret reference
   */
  SecretReference update(SecretReference secretReference, SecretStoreRequest request);

  /**
   * Delete the secret and its reference.
   *
   * @param secretReference the secret reference to delete
   */
  void delete(SecretReference secretReference);
}
