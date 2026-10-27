package io.openaev.database.repository;

import io.openaev.database.model.PayloadIdentity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PayloadIdentityRepository extends CrudRepository<PayloadIdentity, String> {

  @Query(
      "SELECT pi FROM PayloadIdentity pi WHERE pi.managed = true"
          + " AND pi.repository = :repository AND pi.externalId = :externalId")
  Optional<PayloadIdentity> findManaged(
      @Param("repository") String repository, @Param("externalId") String externalId);

  /** Managed identities of an external ID, whatever their repository. */
  @Query(
      "SELECT pi FROM PayloadIdentity pi WHERE pi.managed = true AND pi.externalId = :externalId")
  List<PayloadIdentity> findAllManagedByExternalId(@Param("externalId") String externalId);

  // Concurrent installs of the same payload: the loser does nothing and reads the winner's row.
  @Modifying
  @Query(
      value =
          "INSERT INTO payload_identities (identity_id, tenant_id, payload_repository,"
              + " payload_external_id, payload_source, managed, uninstalled)"
              + " VALUES (gen_random_uuid()::text, :tenantId, :repository, :externalId, :source,"
              + " true, false)"
              + " ON CONFLICT (payload_repository, payload_external_id, tenant_id) DO NOTHING",
      nativeQuery = true)
  void insertManagedIfAbsent(
      @Param("tenantId") String tenantId,
      @Param("repository") String repository,
      @Param("externalId") String externalId,
      @Param("source") String source);

  /** The local identity the legacy collector upsert writes to (one per collector and ID). */
  @Query(
      "SELECT pi FROM PayloadIdentity pi WHERE pi.managed = false"
          + " AND pi.collectorType.id = :collectorTypeId AND pi.externalId = :externalId")
  Optional<PayloadIdentity> findCollectorIdentity(
      @Param("collectorTypeId") String collectorTypeId, @Param("externalId") String externalId);

  /** Local identities claiming this external ID, candidates for a promotion. */
  @Query(
      "SELECT pi FROM PayloadIdentity pi WHERE pi.managed = false AND pi.externalId = :externalId")
  List<PayloadIdentity> findAllLocalByExternalId(@Param("externalId") String externalId);

  @Query(
      "SELECT pi.externalId FROM PayloadIdentity pi WHERE pi.managed = true AND pi.uninstalled = true")
  List<String> findUninstalledExternalIds();
}
