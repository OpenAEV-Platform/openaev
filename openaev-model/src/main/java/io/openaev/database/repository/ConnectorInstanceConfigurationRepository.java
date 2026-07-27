package io.openaev.database.repository;

import io.openaev.database.model.ConnectorInstanceConfiguration;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ConnectorInstanceConfigurationRepository
    extends CrudRepository<ConnectorInstanceConfiguration, String>,
        JpaSpecificationExecutor<ConnectorInstanceConfiguration> {

  List<ConnectorInstanceConfiguration> findByConnectorInstanceId(String connectorInstanceId);

  interface ConnectorIdsFromDatabase {

    String getConnectorInstanceId();

    String getCatalogConnectorId();
  }

  @Query(
      value =
          "SELECT instance.connector_instance_id AS connectorInstanceId, "
              + "instance.connector_instance_catalog_id AS catalogConnectorId "
              + "FROM connector_instance_configurations conf "
              + "JOIN connector_instances instance ON conf.connector_instance_id = instance.connector_instance_id "
              + "WHERE conf.connector_instance_configuration_key = :key "
              + "AND jsonb_exists(conf.connector_instance_configuration_value, :value)",
      nativeQuery = true)
  ConnectorIdsFromDatabase findInstanceAndCatalogIdsByKeyValue(
      @Param("key") String key, @Param("value") String value);

  /**
   * Tenant-scoped variant of {@link #findInstanceAndCatalogIdsByKeyValue}. Native queries bypass
   * the Hibernate tenant {@code @Filter}, so destructive callers (connector deletion resolving the
   * owning instance) must carry the tenant predicate explicitly - otherwise a crafted or colliding
   * configuration value could resolve an instance of another tenant.
   */
  @Query(
      value =
          "SELECT instance.connector_instance_id AS connectorInstanceId, "
              + "instance.connector_instance_catalog_id AS catalogConnectorId "
              + "FROM connector_instance_configurations conf "
              + "JOIN connector_instances instance ON conf.connector_instance_id = instance.connector_instance_id "
              + "WHERE conf.connector_instance_configuration_key = :key "
              + "AND jsonb_exists(conf.connector_instance_configuration_value, :value) "
              + "AND instance.tenant_id = :tenantId",
      nativeQuery = true)
  ConnectorIdsFromDatabase findInstanceAndCatalogIdsByKeyValueAndTenantId(
      @Param("key") String key, @Param("value") String value, @Param("tenantId") String tenantId);

  @Query(
      value =
          """
              SELECT instance.connector_instance_current_status
              FROM connector_instance_configurations conf
              JOIN connector_instances instance ON conf.connector_instance_id = instance.connector_instance_id
              WHERE conf.connector_instance_configuration_key = :key
              AND jsonb_exists(conf.connector_instance_configuration_value, :value)
              """,
      nativeQuery = true)
  Optional<String> findStatusByKeyValue(@Param("key") String key, @Param("value") String value);
}
