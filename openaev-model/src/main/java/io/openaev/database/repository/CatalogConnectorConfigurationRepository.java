package io.openaev.database.repository;

import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.CatalogConnectorConfiguration;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CatalogConnectorConfigurationRepository
    extends CrudRepository<CatalogConnectorConfiguration, String>,
        JpaSpecificationExecutor<CatalogConnectorConfiguration> {
  Optional<CatalogConnectorConfiguration> findByCatalogConnectorAndConnectorConfigurationKey(
      CatalogConnector connector, String connectorConfigurationKey);
}
