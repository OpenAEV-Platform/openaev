package io.openaev.database.repository;

import io.openaev.database.model.ContentRevocation;
import io.openaev.database.model.ContentType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ContentRevocationRepository extends JpaRepository<ContentRevocation, String> {

  // -- REVOCATION LIST (no tenant) --

  List<ContentRevocation> findAllByTypeAndTenantIsNullAndLiftedAtIsNull(ContentType type);

  List<ContentRevocation> findAllByTypeAndExternalIdAndTenantIsNullAndLiftedAtIsNull(
      ContentType type, String externalId);

  List<ContentRevocation> findAllByTypeAndExternalIdInAndTenantIsNullAndLiftedAtIsNull(
      ContentType type, Collection<String> externalIds);

  List<ContentRevocation> findAllByRepositoryAndTenantIsNullAndLiftedAtIsNull(String repository);

  List<ContentRevocation> findAllByTypeAndRepositoryAndExternalIdAndTenantIsNull(
      ContentType type, String repository, String externalId);

  // -- TENANT REVOCATIONS --

  @Query(
      "SELECT r FROM ContentRevocation r WHERE r.payload.id = :payloadId"
          + " AND r.tenant.id = :tenantId AND r.liftedAt IS NULL AND r.liftsRevocationId IS NULL")
  Optional<ContentRevocation> findActiveByPayload(
      @Param("payloadId") String payloadId, @Param("tenantId") String tenantId);

  @Query(
      "SELECT r.payload.id FROM ContentRevocation r WHERE r.payload.id IN :payloadIds"
          + " AND r.tenant.id = :tenantId AND r.liftedAt IS NULL AND r.liftsRevocationId IS NULL")
  List<String> findActivePayloadIds(
      @Param("payloadIds") Collection<String> payloadIds, @Param("tenantId") String tenantId);

  /** Lifts of an inherited revocation, on these variant versions. */
  @Query(
      "SELECT r FROM ContentRevocation r WHERE r.payload.id IN :payloadIds"
          + " AND r.liftsRevocationId IS NOT NULL AND r.liftedAt IS NULL")
  List<ContentRevocation> findActiveLifts(@Param("payloadIds") Collection<String> payloadIds);
}
