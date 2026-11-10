package io.openaev.database.repository;

import io.openaev.database.model.payload.PayloadIdentity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PayloadIdentityRepository extends JpaRepository<PayloadIdentity, String> {

  Optional<PayloadIdentity> findByTenantIdAndRepositoryAndExternalId(
      String tenantId, String repository, String externalId);

  List<PayloadIdentity> findAllByTenantIdAndRepositoryIsNotNull(String tenantId);
}
