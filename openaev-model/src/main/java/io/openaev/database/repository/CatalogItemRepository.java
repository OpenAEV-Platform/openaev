package io.openaev.database.repository;

import io.openaev.database.model.catalog.CatalogItem;
import io.openaev.database.model.catalog.CatalogPayload;
import io.openaev.database.model.catalog.CatalogScenario;
import io.openaev.database.model.catalog.ContentType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface CatalogItemRepository extends JpaRepository<CatalogItem, String> {

  @Query("SELECT s FROM CatalogScenario s")
  List<CatalogScenario> findAllScenarios();

  @Query("SELECT p FROM CatalogPayload p")
  List<CatalogPayload> findAllPayloads();

  List<CatalogItem> findAllByReleaseRepository(String repository);

  Optional<CatalogItem> findByTypeAndExternalIdAndVersion(
      ContentType type, String externalId, String version);
}
