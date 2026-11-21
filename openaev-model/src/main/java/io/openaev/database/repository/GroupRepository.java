package io.openaev.database.repository;

import io.openaev.database.model.Group;
import io.openaev.database.model.Role;
import jakarta.validation.constraints.NotNull;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.util.Assert;

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

  Optional<Group> findByNameAndTenantIsNull(String name);

  List<Group> findAllByNameAndTenantIsNull(String name);

  // -- Tenant-scoped --

  Optional<Group> findByIdAndTenantId(String id, String tenantId);

  Optional<Group> findByIdAndTenantIdIn(String id, Collection<String> tenantIds);

  /**
   * The tenant groups among the given ids that belong to the given tenant. Refuses a null id list,
   * as {@code findAllById} does.
   */
  default List<Group> findAllByIdInTenant(Collection<String> ids, String tenantId) {
    Assert.notNull(ids, "Ids must not be null");
    return findAllByIdInAndTenantId(ids, tenantId);
  }

  List<Group> findAllByIdInAndTenantId(Collection<String> ids, String tenantId);

  List<Group> findAllByTenantId(String tenantId);

  Optional<Group> findByNameAndTenantId(String name, String tenantId);

  List<Group> findAllByTenantIdAndAutoNewMarkingTrue(String tenantId);

  List<Group> findByTenantIdAndName(String tenantId, String name);

  @Query(value = "SELECT user_id FROM users_groups WHERE group_id = :groupId", nativeQuery = true)
  List<String> findUserIdsByGroupId(@Param("groupId") String groupId);

  @Query(value = "SELECT role_id FROM groups_roles WHERE group_id = :groupId", nativeQuery = true)
  Set<String> findRoleIdsByGroupId(@Param("groupId") String groupId);
}
