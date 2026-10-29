package io.openaev.database.repository;

import io.openaev.database.model.ConnectorInstanceLog;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ConnectorInstanceLogRepository
    extends CrudRepository<ConnectorInstanceLog, String>,
        JpaSpecificationExecutor<ConnectorInstanceLog> {

  List<ConnectorInstanceLog> findByConnectorInstanceId(String connectorInstanceId);

  @Query(
      "SELECT l FROM ConnectorInstanceLog l"
          + " WHERE l.connectorInstance.id = :connectorInstanceId"
          + " ORDER BY l.connector_instance_log_created_at ASC")
  List<ConnectorInstanceLog> findOrderedByConnectorInstanceId(
      @Param("connectorInstanceId") String connectorInstanceId);

  // Bulk delete: logs are not indexed nor audited, and StreamApi excludes them from the stream,
  // so skipping the per-row listener events loses nothing.
  @Modifying
  @Query("DELETE FROM ConnectorInstanceLog l WHERE l.connectorInstance.id = :connectorInstanceId")
  void deleteByConnectorInstanceId(@Param("connectorInstanceId") String connectorInstanceId);

  @Query(
      "SELECT l FROM ConnectorInstanceLog l"
          + " WHERE l.connectorInstance.id = :connectorInstanceId"
          + " ORDER BY l.connector_instance_log_created_at DESC")
  Page<ConnectorInstanceLog> searchByConnectorInstanceId(
      @Param("connectorInstanceId") String connectorInstanceId, Pageable pageable);
}
