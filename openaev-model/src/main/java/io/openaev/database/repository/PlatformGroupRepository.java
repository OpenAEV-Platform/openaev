package io.openaev.database.repository;

import io.openaev.database.model.PlatformGroup;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PlatformGroupRepository
    extends JpaRepository<PlatformGroup, String>, JpaSpecificationExecutor<PlatformGroup> {

  @Query(
      value = "SELECT user_id FROM platform_groups_users WHERE platform_group_id = :groupId",
      nativeQuery = true)
  List<String> findUserIdsByGroupId(@Param("groupId") String groupId);

  @Query(
      value =
          "SELECT platform_role_id FROM platform_groups_platform_roles WHERE platform_group_id = :groupId",
      nativeQuery = true)
  Set<String> findPlatformRoleIdsByGroupId(@Param("groupId") String groupId);

  @Modifying
  @Query(value = "DELETE FROM platform_groups WHERE platform_group_id = :id", nativeQuery = true)
  void deleteByIdNative(@Param("id") String id);
}
