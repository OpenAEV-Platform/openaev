package io.openaev.database.repository;

import io.openaev.database.model.Role;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RoleRepository
    extends JpaRepository<Role, String>, JpaSpecificationExecutor<Role> {

  @NotNull
  Optional<Role> findById(@NotNull String id);

  Optional<Role> findByIdAndTenantId(String id, String tenantId);

  List<Role> findAllByTenantId(String tenantId);

  long countByIdIn(Set<String> ids);

  @Modifying
  @Query(value = "DELETE FROM roles WHERE role_id = :id", nativeQuery = true)
  void deleteByIdNative(@Param("id") String id);
}
