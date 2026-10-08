package io.openaev.database.repository;

import io.openaev.database.model.ContentRevocation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContentRevocationRepository extends JpaRepository<ContentRevocation, String> {

  List<ContentRevocation> findAllByTypeAndLiftedAtIsNull(ContentRevocation.CONTENT_TYPE type);

  List<ContentRevocation> findAllByTypeAndExternalIdAndLiftedAtIsNull(
      ContentRevocation.CONTENT_TYPE type, String externalId);
}
