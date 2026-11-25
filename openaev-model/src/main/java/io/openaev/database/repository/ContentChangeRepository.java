package io.openaev.database.repository;

import io.openaev.database.model.catalog.ContentChange;
import io.openaev.database.model.catalog.ContentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContentChangeRepository extends JpaRepository<ContentChange, String> {

  @EntityGraph(attributePaths = "user")
  Page<ContentChange> findAllByTenantIdAndTypeAndRepositoryAndExternalId(
      String tenantId, ContentType type, String repository, String externalId, Pageable pageable);

  @EntityGraph(attributePaths = "user")
  Page<ContentChange> findAllByTenantIdAndTypeAndContentId(
      String tenantId, ContentType type, String contentId, Pageable pageable);
}
