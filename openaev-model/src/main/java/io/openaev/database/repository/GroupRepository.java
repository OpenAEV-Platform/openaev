package io.openaev.database.repository;

import io.openaev.database.model.Group;
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
public interface GroupRepository
    extends JpaRepository<Group, String>, JpaSpecificationExecutor<Group> {

  @NotNull
  Optional<Group> findById(@NotNull String id);

  @NotNull
  List<Group> findAllByRoles(Role role);

  // -- Platform-scoped --

  Optional<Group> findByIdAndTenantIsNull(String id);

  List<Group> findAllByTenantIsNull();

  // -- Tenant-scoped --

  Optional<Group> findByIdAndTenantId(String id, String tenantId);

  List<Group> findAllByTenantId(String tenantId);

  Optional<Group> findByNameAndTenantId(String name, String tenantId);

  Optional<Group> findByName(String name);

  @Query(value = "SELECT user_id FROM users_groups WHERE group_id = :groupId", nativeQuery = true)
  List<String> findUserIdsByGroupId(@Param("groupId") String groupId);

  @Query(value = "SELECT role_id FROM groups_roles WHERE group_id = :groupId", nativeQuery = true)
  Set<String> findRoleIdsByGroupId(@Param("groupId") String groupId);

  @Modifying
  @Query(value = "DELETE FROM groups WHERE group_id = :id", nativeQuery = true)
  void deleteByIdNative(@Param("id") String id);
}
