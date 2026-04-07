package io.openaev.database.repository;

import io.openaev.database.model.CollectorType;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CollectorTypeRepository extends CrudRepository<CollectorType, String> {

  Optional<CollectorType> findByName(@NotNull String name);

  @Modifying
  @Query(value = "DELETE FROM collector_types WHERE tenant_id = :tenantId", nativeQuery = true)
  void deleteAllByTenantIdNative(@Param("tenantId") String tenantId);
}
