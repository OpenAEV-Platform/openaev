package io.openaev.database.repository;

import io.openaev.database.model.CredentialSecretReference;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CredentialSecretReferenceRepository
    extends CrudRepository<CredentialSecretReference, String>,
        JpaSpecificationExecutor<CredentialSecretReference> {

  @Query("select count(sr) from CredentialSecretReference sr")
  long globalCount();
}
