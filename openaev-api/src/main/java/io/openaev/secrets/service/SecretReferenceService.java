package io.openaev.secrets.service;

import io.openaev.database.model.SecretReference;
import io.openaev.database.repository.SecretReferenceRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class SecretReferenceService {
  private final SecretReferenceRepository secretReferenceRepository;

  /**
   * Persists a secret reference.
   *
   * @param secretReference the secret reference to save
   * @return the persisted secret reference
   */
  public SecretReference save(SecretReference secretReference) {
    return secretReferenceRepository.save(
        Objects.requireNonNull(secretReference, "secretReference must not be null"));
  }

  /**
   * Deletes a secret reference.
   *
   * @param secretReference the secret reference to delete
   */
  public void delete(SecretReference secretReference) {
    secretReferenceRepository.delete(
        Objects.requireNonNull(secretReference, "secretReference must not be null"));
  }
}

