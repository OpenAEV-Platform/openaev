package io.openaev.database.repository;

import io.openaev.database.model.ConnectorInstanceLog;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConnectorInstanceLogRepository
    extends CrudRepository<ConnectorInstanceLog, String>,
        JpaSpecificationExecutor<ConnectorInstanceLog> {}
