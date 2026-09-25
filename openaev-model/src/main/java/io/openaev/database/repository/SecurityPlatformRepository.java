package io.openaev.database.repository;

import io.openaev.database.model.AssetType;
import io.openaev.database.model.Document;
import io.openaev.database.model.SecurityPlatform;
import io.openaev.database.raw.RawAssetIndexing;
import jakarta.validation.constraints.NotEmpty;
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
public interface SecurityPlatformRepository
    extends CrudRepository<SecurityPlatform, String>,
        StatisticRepository,
        JpaSpecificationExecutor<SecurityPlatform> {

  Optional<SecurityPlatform> findByExternalReference(
      @Param("externalReference") String externalReference);

  /**
   * Case-insensitive (name, type) lookup mirroring the {@code
   * unique_security_platform_name_type_ci_idx} unique index: used by the upsert endpoint as a
   * fallback when the external reference does not match (e.g. a collector redeployed through the
   * Integration Manager registers with a freshly generated collector id).
   */
  Optional<SecurityPlatform> findByNameIgnoreCaseAndSecurityPlatformType(
      String name, SecurityPlatform.SECURITY_PLATFORM_TYPE securityPlatformType);

  /**
   * Case-insensitive exact-name lookup (smallest id wins for determinism): used by the V1 importer
   * to re-attach detection remediations to an existing platform by name.
   */
  Optional<SecurityPlatform> findFirstByNameIgnoreCaseOrderByIdAsc(String name);

  Optional<SecurityPlatform> findByIdAndTenantId(String id, String tenantId);

  @Override
  @Query(
      "select COUNT(DISTINCT a) from Inject i "
          + "join i.assets as a "
          + "join i.exercise as e "
          + "join e.grants as grant "
          + "join grant.group.users as user "
          + "where user.id = :userId and i.createdAt > :creationDate")
  long userCount(@Param("userId") String userId, @Param("creationDate") Instant creationDate);

  @Override
  @Query("select count(distinct s) from SecurityPlatform s where s.createdAt > :creationDate")
  long globalCount(@Param("creationDate") Instant creationDate);

  @Query(
      "select distinct s.logoDark from SecurityPlatform s "
          + "union "
          + "select distinct s.logoLight from SecurityPlatform s ")
  List<Document> securityPlatformLogo();

  @Query(
      value =
          "SELECT a.asset_id, a.asset_name, a.asset_created_at, a.asset_updated_at, a.tenant_id "
              + "FROM assets a "
              + "WHERE a.asset_updated_at > :from AND a.asset_type = '"
              + AssetType.Values.SECURITY_PLATFORM_TYPE
              + "' "
              + "ORDER BY a.asset_updated_at LIMIT :limit;",
      nativeQuery = true)
  List<RawAssetIndexing> findForIndexing(@Param("from") Instant from, @Param("limit") int limit);

  @Query(
      "SELECT DISTINCT a FROM Asset a "
          + "WHERE a.type = '"
          + AssetType.Values.SECURITY_PLATFORM_TYPE
          + "' AND "
          + "(:name IS NULL OR lower(a.name) LIKE concat('%', lower(cast(coalesce(:name, '') as string)), '%'))")
  List<SecurityPlatform> findAllByName(String name);

  @Query(
      "SELECT DISTINCT a FROM Asset a "
          + "WHERE a.type = '"
          + AssetType.Values.SECURITY_PLATFORM_TYPE
          + "' AND "
          + "a.id IN :ids")
  List<SecurityPlatform> findAllByIds(@NotEmpty @Param("ids") Set<String> ids);
}
