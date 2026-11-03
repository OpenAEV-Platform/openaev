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

  List<CatalogItem> findAllByRepository(String repository);

  List<CatalogItem> findAllByTypeAndExternalId(ContentType type, String externalId);

  List<CatalogItem> findAllByTypeAndExternalIdAndVersion(
      ContentType type, String externalId, String version);

  Optional<CatalogItem> findByTypeAndRepositoryAndExternalIdAndVersion(
      ContentType type, String repository, String externalId, String version);

  List<CatalogItem> findAllByTypeAndRepositoryAndExternalId(
      ContentType type, String repository, String externalId);
}
