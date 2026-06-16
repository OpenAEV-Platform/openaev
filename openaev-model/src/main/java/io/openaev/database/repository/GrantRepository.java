package io.openaev.database.repository;

import io.openaev.database.model.Grant;
import io.openaev.database.raw.RawGrant;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GrantRepository
    extends CrudRepository<Grant, String>, JpaSpecificationExecutor<Grant> {

  @NotNull
  Optional<Grant> findById(@NotNull String id);

  @Query(
      value =
          "SELECT users_groups.user_id, grants.grant_name, grants.grant_id "
              + "FROM grants "
              + "LEFT JOIN groups ON grants.grant_group = groups.group_id "
              + "LEFT JOIN users_groups ON groups.group_id = grants.grant_group "
              + "WHERE grants.grant_resource_type = 'SIMULATION' and grants.grant_resource IN :ids ;",
      nativeQuery = true)
  List<RawGrant> rawByExerciseIds(@Param("ids") List<String> ids);

  @Query(
      "SELECT CASE WHEN COUNT(g) > 0 THEN true ELSE false END "
          + "FROM Grant g "
          + "JOIN g.group gr "
          + "JOIN gr.users u "
          + "WHERE u.id = :userId "
          + "AND g.resourceId = :resourceId "
          + "AND g.name IN :grantTypes")
  boolean existsByUserIdAndResourceIdAndNameIn(
      @Param("userId") String userId,
      @Param("resourceId") String resourceId,
      @Param("grantTypes") List<Grant.GRANT_TYPE> grantTypes);

  @Modifying
  @Query(
      "UPDATE Grant g SET g.resourceId = :newId WHERE g.resourceId = :currentId AND g.grantResourceType = :resourceType")
  int updateGrantResourceIdAndType(
      String currentId, String newId, Grant.GRANT_RESOURCE_TYPE resourceType);
}
