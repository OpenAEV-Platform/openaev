package io.openaev.database.repository;

import io.openaev.database.model.CatalogSyncState;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CatalogSyncStateRepository extends JpaRepository<CatalogSyncState, String> {

  Optional<CatalogSyncState> findByRepositoryAndChannel(String repository, String channel);
}
