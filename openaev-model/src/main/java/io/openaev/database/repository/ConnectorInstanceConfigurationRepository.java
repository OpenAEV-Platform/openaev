package io.openaev.database.repository;

import io.openaev.database.model.ConnectorInstanceConfiguration;
import java.util.List;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConnectorInstanceConfigurationRepository
    extends CrudRepository<ConnectorInstanceConfiguration, String>,
        JpaSpecificationExecutor<ConnectorInstanceConfiguration> {

  List<ConnectorInstanceConfiguration> findByConnectorInstanceId(String connectorInstanceId);
}
