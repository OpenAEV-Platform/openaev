package io.openaev.database.repository;

import io.openaev.database.model.ConnectorInstanceLog;
import java.util.List;
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

  long countByConnectorInstanceId(String connectorInstanceId);

  @Modifying
  @Query(
      value =
          "DELETE FROM connector_instance_logs "
              + "WHERE ctid IN ("
              + "  SELECT ctid FROM connector_instance_logs "
              + "  WHERE connector_instance_id = :instanceId "
              + "  ORDER BY connector_instance_log_created_at ASC "
              + "  LIMIT :limit"
              + ")",
      nativeQuery = true)
  void deleteOldestLogByConnectorInstanceId(
      @Param("instanceId") String instanceId, @Param("limit") long limit);

  List<ConnectorInstanceLog> findByConnectorInstanceId(String connectorInstanceId);
}
