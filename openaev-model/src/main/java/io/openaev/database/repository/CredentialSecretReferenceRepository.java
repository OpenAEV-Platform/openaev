package io.openaev.database.repository;

import io.openaev.database.model.CredentialSecretReference;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface CredentialSecretReferenceRepository
    extends JpaRepository<CredentialSecretReference, String>,
        JpaSpecificationExecutor<CredentialSecretReference> {

  @NotNull
  Optional<CredentialSecretReference> findById(@NotNull String id);
}
