package io.openaev.database.repository;

import io.openaev.database.model.catalog.CatalogItem;
import io.openaev.database.model.catalog.CatalogPayload;
import io.openaev.database.model.catalog.CatalogScenario;
import io.openaev.database.model.catalog.ContentType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CatalogItemRepository extends JpaRepository<CatalogItem, String> {

  // -- ALL ITEMS --

  List<CatalogItem> findAllByReleaseRepository(String repository);

  Optional<CatalogItem> findByTypeAndExternalIdAndVersion(
      ContentType type, String externalId, String version);

  @Query(
      "SELECT i FROM CatalogItem i WHERE TYPE(i) = :type"
          + " AND i.externalId = :externalId AND i.version = :version")
  <T extends CatalogItem> Optional<T> findVersion(
      @Param("type") Class<T> type,
      @Param("externalId") String externalId,
      @Param("version") String version);

  // -- SCENARIOS --

  @Query("SELECT s FROM CatalogScenario s")
  List<CatalogScenario> findAllScenarios();

  // -- PAYLOADS --

  @Query("SELECT p FROM CatalogPayload p")
  List<CatalogPayload> findAllPayloads();
}
