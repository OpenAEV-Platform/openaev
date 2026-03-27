package io.openaev.database.repository;

import io.openaev.database.model.Tenant;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TenantRepository
    extends CrudRepository<Tenant, String>, JpaSpecificationExecutor<Tenant> {

  // -- READ --

  /** Returns all tenants a given user has access to via the users_tenants join table. */
  @Query(
      value =
          "SELECT t.* FROM tenants t"
              + " JOIN users_tenants ut ON ut.tenant_id = t.tenant_id"
              + " WHERE ut.user_id = :userId AND t.tenant_deleted_at IS NULL"
              + " ORDER BY t.tenant_name",
      nativeQuery = true)
  List<Tenant> findTenantsByUserId(@Param("userId") String userId);

  /** Returns soft-deleted tenants whose grace period has expired. */
  @Query("SELECT t FROM Tenant t WHERE t.deletedAt IS NOT NULL AND t.deletedAt < :cutoffDate")
  List<Tenant> findAllExpiredSoftDeleted(@Param("cutoffDate") Instant cutoffDate);

  // -- WRITE --

  /** Links a user to a tenant. Does nothing if the link already exists. */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          "INSERT INTO users_tenants (user_id, tenant_id) VALUES (:userId, :tenantId)"
              + " ON CONFLICT DO NOTHING",
      nativeQuery = true)
  void addUserToTenant(@Param("userId") String userId, @Param("tenantId") String tenantId);

  // -- DELETE --

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = "DELETE FROM tenants t WHERE t.tenant_id IN :tenantIds", nativeQuery = true)
  void deleteAllByIdsNative(@Param("tenantIds") List<String> tenantIds);
}
