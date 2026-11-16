package io.openaev.database.repository;

import io.openaev.database.model.payload.PayloadRevocation;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PayloadRevocationRepository extends JpaRepository<PayloadRevocation, String> {

  boolean existsByTenantIdAndPayloadId(String tenantId, String payloadId);

  Optional<PayloadRevocation> findByTenantIdAndPayloadId(String tenantId, String payloadId);
}
