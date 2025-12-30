package io.openaev.database.repository;

import io.openaev.database.model.ConnectorInstancePersisted;
import io.openaev.database.model.ConnectorType;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConnectorInstanceRepository
    extends CrudRepository<ConnectorInstancePersisted, String>,
        JpaSpecificationExecutor<ConnectorInstancePersisted> {

  @EntityGraph(attributePaths = {"configurations", "catalogConnector"})
  @Query(
      "SELECT DISTINCT instance FROM ConnectorInstancePersisted instance "
          + "WHERE instance.catalogConnector.containerImage IS NOT NULL "
          + "AND instance.catalogConnector.isManagerSupported = TRUE")
  List<ConnectorInstancePersisted> findAllManagedByXtmComposerAndConfiguration();

  List<ConnectorInstancePersisted> findAllByCatalogConnectorId(String catalogConnectorId);

  @EntityGraph(attributePaths = {"configurations", "catalogConnector"})
  List<ConnectorInstancePersisted> findAllByCatalogConnectorContainerType(
      ConnectorType containerType);
}
