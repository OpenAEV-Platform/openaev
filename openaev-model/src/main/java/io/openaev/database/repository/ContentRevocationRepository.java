package io.openaev.database.repository;

import io.openaev.database.model.ContentRevocation;
import io.openaev.database.model.ContentType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContentRevocationRepository extends JpaRepository<ContentRevocation, String> {

  List<ContentRevocation> findAllByTypeAndLiftedAtIsNull(ContentType type);

  List<ContentRevocation> findAllByTypeAndExternalIdAndLiftedAtIsNull(
      ContentType type, String externalId);
}
