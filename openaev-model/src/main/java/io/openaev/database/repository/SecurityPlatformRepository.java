package io.openaev.database.repository;

import io.openaev.database.model.AssetType;
import io.openaev.database.model.Document;
import io.openaev.database.model.SecurityPlatform;
import io.openaev.database.raw.RawAsset;
import io.openaev.utils.Constants;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
          "SELECT a.asset_id, a.asset_name, a.asset_created_at, a.asset_updated_at "
              + "FROM assets a "
              + "WHERE a.asset_updated_at > :from AND a.asset_type = '"
              + AssetType.Values.SECURITY_PLATFORM_TYPE
              + "' "
              + "GROUP BY a.asset_id, a.asset_updated_at "
              + "ORDER BY a.asset_updated_at LIMIT "
              + Constants.INDEXING_RECORD_SET_SIZE
              + ";",
      nativeQuery = true)
  List<RawAsset> findForIndexing(@Param("from") Instant from);

  @Query(
      "SELECT DISTINCT a FROM Asset a "
          + "WHERE a.type = '"
          + AssetType.Values.SECURITY_PLATFORM_TYPE
          + "' AND "
          + "(:name IS NULL OR lower(a.name) LIKE lower(concat('%', cast(coalesce(:name, '') as string), '%')))")
  List<SecurityPlatform> findAllByName(String name);
}
