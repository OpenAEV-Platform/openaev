package io.openaev.database.repository;

import io.openaev.database.model.payload.PayloadIdentity;
import io.openaev.database.raw.RawInstalledPayloadVersion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
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
          + " WHERE i.tenant.id = :tenantId")
  List<RawInstalledPayloadVersion> findInstalledVersions(@Param("tenantId") String tenantId);
}
