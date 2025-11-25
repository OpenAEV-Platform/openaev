package io.openaev.database.repository;

import io.openaev.database.model.CatalogConnector;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CatalogConnectorRepository
    extends CrudRepository<CatalogConnector, String>, JpaSpecificationExecutor<CatalogConnector> {
  Optional<CatalogConnector> findByTitle(String title);

  @Query(
      "SELECT c FROM CatalogConnector c LEFT JOIN FETCH c.catalogConnectorConfigurations WHERE c.slug = :slug")
  Optional<CatalogConnector> findBySlugWithConfigurations(String slug);
}
