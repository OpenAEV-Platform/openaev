package io.openaev.database.repository;

import io.openaev.database.model.PlatformRole;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PlatformRoleRepository
    extends JpaRepository<PlatformRole, String>, JpaSpecificationExecutor<PlatformRole> {

  long countByIdIn(Set<String> ids);

  @Modifying
  @Query(value = "DELETE FROM platform_roles WHERE platform_role_id = :id", nativeQuery = true)
  void deleteByIdNative(@Param("id") String id);
}
