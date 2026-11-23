package io.openaev.database.repository;

import io.openaev.database.model.catalog.CatalogItem;
import io.openaev.database.model.catalog.ContentType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CatalogItemRepository
    extends JpaRepository<CatalogItem, String>, JpaSpecificationExecutor<CatalogItem> {

  // -- ALL ITEMS --

  List<CatalogItem> findAllByReleaseRepository(String repository);

  boolean existsByTypeAndExternalId(ContentType type, String externalId);

  @Query("SELECT i FROM CatalogItem i WHERE TYPE(i) = :type")
  <T extends CatalogItem> List<T> findAllOfType(@Param("type") Class<T> type);

  @Query(
      "SELECT i FROM CatalogItem i WHERE TYPE(i) = :type AND i.latestOffered = true"
          + " ORDER BY i.name")
  <T extends CatalogItem> List<T> findAllLatestOffered(@Param("type") Class<T> type);

  @Query(
      "SELECT i FROM CatalogItem i WHERE TYPE(i) = :type"
          + " AND i.externalId = :externalId AND i.version = :version")
  <T extends CatalogItem> Optional<T> findVersion(
      @Param("type") Class<T> type,
      @Param("externalId") String externalId,
      @Param("version") String version);

  @Query("SELECT i FROM CatalogItem i WHERE TYPE(i) = :type AND i.externalId = :externalId")
  <T extends CatalogItem> List<T> findAllVersions(
      @Param("type") Class<T> type, @Param("externalId") String externalId);
}
