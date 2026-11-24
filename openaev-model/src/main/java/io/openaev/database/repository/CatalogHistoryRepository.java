package io.openaev.database.repository;

import io.openaev.database.model.catalog.CatalogHistory;
import io.openaev.database.model.catalog.ContentType;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CatalogHistoryRepository extends JpaRepository<CatalogHistory, String> {

  @EntityGraph(attributePaths = "user")
  List<CatalogHistory> findAllByTenantIdAndTypeAndRepositoryAndExternalIdOrderByCreatedAtDesc(
      String tenantId, ContentType type, String repository, String externalId);

  @EntityGraph(attributePaths = "user")
  List<CatalogHistory> findAllByTenantIdAndTypeAndContentIdOrderByCreatedAtDesc(
      String tenantId, ContentType type, String contentId);
}
