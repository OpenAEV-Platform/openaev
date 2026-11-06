package io.openaev.database.repository;

import io.openaev.database.model.catalog.CatalogRelease;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CatalogReleaseRepository extends JpaRepository<CatalogRelease, String> {

  Optional<CatalogRelease> findFirstByRepositoryOrderByCreatedAtDesc(String repository);
}
