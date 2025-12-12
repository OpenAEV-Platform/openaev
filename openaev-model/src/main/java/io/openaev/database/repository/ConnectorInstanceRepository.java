package io.openaev.database.repository;

import io.openaev.database.model.ConnectorInstancePersisted;
import java.util.List;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConnectorInstanceRepository
    extends CrudRepository<ConnectorInstancePersisted, String>,
        JpaSpecificationExecutor<ConnectorInstancePersisted> {

  List<ConnectorInstancePersisted> findByCatalogConnectorId(String catalogConnectorId);
}
