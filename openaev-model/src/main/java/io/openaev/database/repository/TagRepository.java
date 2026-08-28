package io.openaev.database.repository;

import io.openaev.database.model.Tag;
import io.openaev.database.raw.RawTagIndexing;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TagRepository extends CrudRepository<Tag, String>, JpaSpecificationExecutor<Tag> {

  long countByIdIn(Set<String> ids);

  @NotNull
  Optional<Tag> findById(@NotNull String id);

  @NotNull
  Optional<Tag> findByName(@NotNull final String name);

  /**
   * Tenant-scoped primary-key lookup. Hibernate's {@code tenantFilter} does not apply to {@code
   * findById} (filters never apply to primary-key loads), so callers resolving an id received from
   * user input (e.g. import files) must use this method to avoid reading another tenant's tag.
   */
  @NotNull
  Optional<Tag> findByIdAndTenantId(@NotNull String id, @NotNull String tenantId);

  // TODO v2: once tags get v2 activated
  // https://github.com/OpenAEV-Platform/openaev/issues/6424, the SQL rewriter will scope findByName
  // automatically - remove this tenant-scoped variant and switch callers to findByName
  @NotNull
  Optional<Tag> findByNameAndTenantId(@NotNull String name, @NotNull String tenantId);

  @NotNull
  List<Tag> findByNameIgnoreCase(@NotNull final String name);

  @Query(
      value =
          "SELECT t.tag_id, t.tag_name, t.tag_color, t.tag_created_at, t.tag_updated_at, t.tenant_id "
              + "FROM tags t "
              + "JOIN assets_tags at ON t.tag_id = at.tag_id "
              + "JOIN assets a ON at.asset_id = a.asset_id "
              + "WHERE at.asset_id = :assetId "
              + "AND a.tenant_id = :tenantId "
              + "AND t.tenant_id = :tenantId",
      nativeQuery = true)
  Set<Tag> findByAssetIdAndTenantId(
      @Param("assetId") String assetId, @Param("tenantId") String tenantId);

  @Query(
      value =
          "SELECT t.tag_id, t.tag_name, t.tag_color, "
              + "t.tag_created_at, t.tag_updated_at, t.tenant_id "
              + "FROM tags t "
              + "WHERE t.tag_updated_at > :from ORDER BY t.tag_updated_at LIMIT :limit;",
      nativeQuery = true)
  List<RawTagIndexing> findForIndexing(@Param("from") Instant from, @Param("limit") int limit);
}
