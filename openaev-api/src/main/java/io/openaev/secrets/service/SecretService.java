package io.openaev.secrets.service;

import io.openaev.database.model.Secret;
import io.openaev.database.repository.SecretsRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class SecretService {
  private final SecretsRepository secretsRepository;

  /**
   * Finds an existing secret by identifier and tenant.
   *
   * @param secretId the identifier of the secret to retrieve
   * @return the existing tenant-scoped secret
   * @throws IllegalArgumentException if no secret exists for the given id and tenant
   */
  public Secret findByIdOrThrow(String secretId) {
    String id = Objects.requireNonNull(secretId, "secretId must not be null");
    return secretsRepository
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Secret not found for id: " + id));
  }

  /**
   * Persists a secret.
   *
   * @param secret the secret to save
   * @return the persisted secret
   */
  public Secret save(Secret secret) {
    return secretsRepository.save(Objects.requireNonNull(secret, "secret must not be null"));
  }

  /**
   * Deletes a secret by identifier.
   *
   * @param secretId the identifier of the secret to delete
   */
  public void deleteById(String secretId) {
    secretsRepository.deleteById(Objects.requireNonNull(secretId, "secretId must not be null"));
  }
}
