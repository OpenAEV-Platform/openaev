package io.openaev.database.repository;

import io.openaev.database.model.payload.PayloadIdentity;
import io.openaev.database.raw.RawInstalledPayloadVersion;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PayloadIdentityRepository extends JpaRepository<PayloadIdentity, String> {

  // -- ONE IDENTITY --

  Optional<PayloadIdentity> findByTenantIdAndRepositoryAndExternalId(
      String tenantId, String repository, String externalId);

  // -- CATALOGUE IDENTITIES --

  List<PayloadIdentity> findAllByTenantIdAndRepositoryIsNotNull(String tenantId);

  // -- INSTALLED VERSIONS --

  @Query(
      "SELECT i.repository AS repository, i.externalId AS externalId, v.version AS version"
          + " FROM PayloadIdentity i JOIN i.latestVersion v"
          + " WHERE i.tenant.id = :tenantId AND i.repository IS NOT NULL")
  List<RawInstalledPayloadVersion> findInstalledVersions(@Param("tenantId") String tenantId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT i FROM PayloadIdentity i WHERE i.id = :id")
  Optional<PayloadIdentity> findForUpdateById(@Param("id") String id);
}
