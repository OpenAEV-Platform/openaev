package io.openaev.database.repository;

import io.openaev.database.model.Injector;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InjectorRepository
    extends JpaRepository<Injector, String>, JpaSpecificationExecutor<Injector> {

  @NotNull
  Optional<Injector> findById(@NotNull String id);

  @NotNull
  Optional<Injector> findByTypeAndTenantId(@NotNull String type, @NotNull String tenantId);

  List<Injector> findAllByPayloadsAndTenantId(@NotNull Boolean payloads, @NotNull String tenantId);

  @Modifying
  @Query(value = "DELETE FROM injectors WHERE tenant_id = :tenantId", nativeQuery = true)
  void deleteAllByTenantIdNative(@Param("tenantId") String tenantId);
}
