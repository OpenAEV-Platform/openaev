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

  // -- Tenant-scoped --

  Optional<Setting> findByKeyAndTenantId(@NotNull String key, @NotNull String tenantId);

  List<Setting> findAllByTenantId(@NotNull String tenantId);

  @Query(value = "SHOW server_version", nativeQuery = true)
  String getServerVersion();

  /**
   * Bulk delete, so concurrent callers cannot collide on a row another one already removed (which
   * Hibernate reports as an OptimisticLockException at flush). Skips the remove lifecycle
   * callbacks, so it stays reserved for settings that are neither indexed nor audit-diff tracked.
   */
  @Transactional
  @Modifying
  @Query(value = "delete from parameters where parameter_key in (:keys)", nativeQuery = true)
  void deleteByKeyIn(@NotNull @Param("keys") final Collection<String> keys);
}
