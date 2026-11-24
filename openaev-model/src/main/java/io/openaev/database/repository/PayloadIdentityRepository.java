package io.openaev.database.repository;

import io.openaev.database.model.payload.PayloadIdentity;
import io.openaev.database.raw.RawInstalledCatalogVersion;
import io.openaev.database.raw.RawOlderVersionUsages;
import jakarta.persistence.LockModeType;
import java.util.Collection;
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
  List<RawInstalledCatalogVersion> findInstalledVersions(@Param("tenantId") String tenantId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT i FROM PayloadIdentity i WHERE i.id = :id")
  Optional<PayloadIdentity> findForUpdateById(@Param("id") String id);

  // -- OLDER VERSIONS IN USE --

  /**
   * Of these actions, how many injects and chained steps still run another version of their
   * payload, unless they were kept there for this version.
   */
  // Native: shares the usage rules of the inject and step native queries; a read.
  @Query(
      value =
          """
          SELECT ic.injector_contract_id AS actionId,
            (SELECT COUNT(*) FROM injects i WHERE i.tenant_id = :tenantId AND \s"""
              + InjectRepository.OUTSIDE_SIMULATIONS
              + " AND i.inject_injector_contract IN ("
              + OTHER_VERSION_ACTIONS
              + ")) + (SELECT COUNT(*) "
              + StepRepository.SCENARIO_STEP_TEMPLATES
              + " AND "
              + StepRepository.STEP_CONTRACT_ID
              + " IN ("
              + OTHER_VERSION_ACTIONS
              + """
              )) AS usages
          FROM injectors_contracts ic
          JOIN payloads p ON p.payload_id = ic.injector_contract_payload AND p.tenant_id = ic.tenant_id
          JOIN payload_identities pi ON pi.payload_identity_id = p.payload_identity
          WHERE ic.tenant_id = :tenantId AND ic.injector_contract_id IN (:actionIds)
            AND (pi.payload_identity_older_versions_kept_for IS NULL
              OR pi.payload_identity_older_versions_kept_for <> p.payload_id)""",
      nativeQuery = true)
  List<RawOlderVersionUsages> countOlderVersionUsagesByTenantId(
      @Param("tenantId") String tenantId, @Param("actionIds") Collection<String> actionIds);

  /** The actions of the other versions of the payload of `p`. */
  String OTHER_VERSION_ACTIONS =
      """
      SELECT vc.injector_contract_id FROM payloads v
      JOIN injectors_contracts vc
        ON vc.injector_contract_payload = v.payload_id AND vc.tenant_id = v.tenant_id
      WHERE v.tenant_id = :tenantId AND v.payload_identity = p.payload_identity
        AND v.payload_id <> p.payload_id""";
}
