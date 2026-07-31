package io.openaev.database.repository;

import io.openaev.database.model.CredentialSecretReference;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface CredentialSecretReferenceRepository
    extends JpaRepository<CredentialSecretReference, String>,
        JpaSpecificationExecutor<CredentialSecretReference> {

  Optional<CredentialSecretReference> findByIdAndTenantId(String id, String tenantId);
}
