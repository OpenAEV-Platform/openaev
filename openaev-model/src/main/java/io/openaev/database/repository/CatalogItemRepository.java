package io.openaev.database.repository;

import io.openaev.database.model.CatalogItem;
import io.openaev.database.model.ContentType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CatalogItemRepository extends JpaRepository<CatalogItem, String> {

  List<CatalogItem> findAllByType(ContentType type);

  List<CatalogItem> findAllByTypeAndExternalId(ContentType type, String externalId);

  Optional<CatalogItem> findByTypeAndExternalIdAndVersion(
      ContentType type, String externalId, String version);
}
