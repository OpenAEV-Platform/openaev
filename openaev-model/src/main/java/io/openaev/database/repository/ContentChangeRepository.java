package io.openaev.database.repository;

import io.openaev.database.model.catalog.ContentChange;
import io.openaev.database.model.catalog.ContentType;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContentChangeRepository extends JpaRepository<ContentChange, String> {

  @EntityGraph(attributePaths = "user")
  List<ContentChange> findAllByTenantIdAndTypeAndRepositoryAndExternalIdOrderByCreatedAtDesc(
      String tenantId, ContentType type, String repository, String externalId);

  @EntityGraph(attributePaths = "user")
  List<ContentChange> findAllByTenantIdAndTypeAndContentIdOrderByCreatedAtDesc(
      String tenantId, ContentType type, String contentId);
}
