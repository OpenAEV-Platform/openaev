package io.openaev.database.repository;

import io.openaev.database.model.ConnectorInstancePersisted;
import io.openaev.database.model.ConnectorType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConnectorInstanceRepository
    extends CrudRepository<ConnectorInstancePersisted, String>,
        JpaSpecificationExecutor<ConnectorInstancePersisted> {

  @Query(
      value =
          "SELECT DISTINCT ci.* FROM connector_instances ci "
              + "JOIN catalog_connectors cc ON ci.connector_instance_catalog_id = cc.catalog_connector_id "
              + "WHERE cc.catalog_connector_container_image IS NOT NULL "
              + "AND cc.catalog_connector_manager_supported = TRUE ",
      nativeQuery = true)
  List<ConnectorInstancePersisted> findAllManagedByXtmComposerAndConfiguration();

  List<ConnectorInstancePersisted> findAllByCatalogConnectorId(String catalogConnectorId);

  @EntityGraph(attributePaths = {"configurations", "catalogConnector"})
  List<ConnectorInstancePersisted> findAllByTenantIdAndCatalogConnectorClassName(
      String tenantId, String className);

  /** Loads a single instance with configurations and catalogConnector eagerly initialized. */
  @EntityGraph(attributePaths = {"configurations", "catalogConnector"})
  Optional<ConnectorInstancePersisted> findWithGraphById(String id);

  @EntityGraph(attributePaths = {"configurations", "catalogConnector"})
  List<ConnectorInstancePersisted> findAllByCatalogConnectorContainerType(
      ConnectorType containerType);
}
