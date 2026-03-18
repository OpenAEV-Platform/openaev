package io.openaev.database.repository;

import io.openaev.database.model.PlatformGroup;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PlatformGroupRepository
    extends CrudRepository<PlatformGroup, String>, JpaSpecificationExecutor<PlatformGroup> {

  @Query(
      value = "SELECT user_id FROM platform_groups_users WHERE platform_group_id = :groupId",
      nativeQuery = true)
  List<String> findUserIdsByGroupId(@Param("groupId") String groupId);

  @Query(
      value =
          "SELECT platform_role_id FROM platform_groups_platform_roles WHERE platform_group_id = :groupId",
      nativeQuery = true)
  Set<String> findPlatformRoleIdsByGroupId(@Param("groupId") String groupId);
}
