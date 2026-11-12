package io.openaev.database.repository;

import io.openaev.database.model.Setting;
import jakarta.validation.constraints.NotNull;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface SettingRepository
    extends CrudRepository<Setting, String>, JpaSpecificationExecutor<Setting> {

  @NotNull
  Optional<Setting> findById(@NotNull final String id);

  List<Setting> findAllByKeyIn(List<String> keys);

  // -- Platform-scoped --

  Optional<Setting> findByKeyAndTenantIsNull(@NotNull String key);

  List<Setting> findAllByTenantIsNull();

  List<Setting> findAllByKeyInAndTenantIsNull(List<String> keys);

  List<Setting> findAllByKeyStartingWithAndTenantIsNull(@NotNull String prefix);

  // -- Tenant-scoped --

  Optional<Setting> findByKeyAndTenantId(@NotNull String key, @NotNull String tenantId);

  List<Setting> findAllByTenantId(@NotNull String tenantId);

  // Native: an atomic upsert (ON CONFLICT DO NOTHING on the unique index of the platform rows) that
  // the ORM cannot express, so two writers of a first banner row never collide. No session side
  // effect is lost: the banner is read through the computed platform_banner_by_level of the
  // settings endpoints, not through an entity event, and Setting is neither indexed nor audited.
  @Modifying
  @Query(
      value =
          "INSERT INTO parameters (parameter_id, parameter_key, parameter_value, tenant_id)"
              + " VALUES (:id, :key, :value, NULL)"
              + " ON CONFLICT (parameter_key) WHERE tenant_id IS NULL DO NOTHING",
      nativeQuery = true)
  int insertPlatformSettingIfAbsent(
      @Param("id") String id, @Param("key") String key, @Param("value") String value);

  // Native: a transaction-level advisory lock, which the ORM cannot express. It touches no row and
  // no entity, so no session side effect is lost.
  @Query(value = "SELECT CAST(pg_advisory_xact_lock(hashtext(:name)) AS text)", nativeQuery = true)
  String lockUntilEndOfTransaction(@Param("name") String name);

  @Query(value = "SHOW server_version", nativeQuery = true)
  String getServerVersion();

  @Transactional
  void deleteByKeyIn(@NotNull final Collection<String> keys);
}
