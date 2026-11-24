package io.openaev.database.repository;

import io.openaev.database.model.payload.PayloadRevocation;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PayloadRevocationRepository extends JpaRepository<PayloadRevocation, String> {

  // -- ONE VERSION --

  boolean existsByTenantIdAndPayloadId(String tenantId, String payloadId);

  Optional<PayloadRevocation> findByTenantIdAndPayloadId(String tenantId, String payloadId);

  // -- SEVERAL VERSIONS --

  boolean existsByTenantIdAndPayloadIdIn(String tenantId, Collection<String> payloadIds);

  List<PayloadRevocation> findAllByTenantIdAndPayloadIdIn(
      String tenantId, Collection<String> payloadIds);

  // -- WHOLE TENANT --

  List<PayloadRevocation> findAllByTenantId(String tenantId);
}
