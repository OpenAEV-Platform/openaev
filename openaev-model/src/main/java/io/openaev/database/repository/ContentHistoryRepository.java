package io.openaev.database.repository;

import io.openaev.database.model.ContentHistory;
import io.openaev.database.model.ContentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContentHistoryRepository
    extends CrudRepository<ContentHistory, String>, JpaSpecificationExecutor<ContentHistory> {

  Page<ContentHistory> findAllByTypeAndExternalIdOrderByCreatedAtDesc(
      ContentType type, String externalId, Pageable pageable);
}
