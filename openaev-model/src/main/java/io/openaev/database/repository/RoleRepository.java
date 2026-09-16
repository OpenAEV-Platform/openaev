package io.openaev.database.repository;

import io.openaev.database.model.Role;
import jakarta.validation.constraints.NotNull;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface RoleRepository
    extends JpaRepository<Role, String>, JpaSpecificationExecutor<Role> {

  @NotNull
  Optional<Role> findById(@NotNull String id);

  Optional<Role> findByIdAndTenantId(String id, String tenantId);

  List<Role> findAllByIdInAndTenantId(Collection<String> ids, String tenantId);

  List<Role> findAllByTenantId(String tenantId);
}
