package io.openaev.telemetry.metric_collectors;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.openaev.database.model.BaseConnectorEntity;
import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.ConnectorInstancePersisted;
import io.openaev.database.model.ConnectorType;
import io.openaev.database.repository.CollectorRepository;
import io.openaev.database.repository.ConnectorInstanceRepository;
import io.openaev.database.repository.ExecutorRepository;
import io.openaev.database.repository.InjectorRepository;
import io.opentelemetry.api.common.Attributes;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Telemetry inventory of the deployed injectors, collectors and executors, broken down by catalog
 * identity. Components deployed from the catalog (XTM Composer) resolve to their catalog connector
 * slug through the connector_instances join (the user-set label is irrelevant); manually deployed
 * components fall back to their own code-level type slug, flagged managed=false. Same resolution
 * as {@code AbstractConnectorService.getConnectorRelationsId()}. Anonymous only: identity slugs
 * and counts, never any component configuration.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryMetricCollector {

  private static final String ATTRIBUTE_SLUG = "slug";
  private static final String ATTRIBUTE_MANAGED = "managed";
  private static final String ATTRIBUTE_TYPE = "type";

  private final MetricRegistry metricRegistry;
  private final InjectorRepository injectorRepository;
  private final CollectorRepository collectorRepository;
  private final ExecutorRepository executorRepository;
  private final ConnectorInstanceRepository connectorInstanceRepository;

  @PostConstruct
  public void init() {
    metricRegistry.registerMultiGauge(
        "injectors_deployed_by_identity",
        "Deployed injectors broken down by catalog identity (slug, managed, type)",
        () -> collectInventory(ConnectorType.INJECTOR, injectorRepository.findAll()));
    metricRegistry.registerMultiGauge(
        "collectors_deployed_by_identity",
        "Deployed collectors broken down by catalog identity (slug, managed, type)",
        () -> collectInventory(ConnectorType.COLLECTOR, collectorRepository.findAll()));
    metricRegistry.registerMultiGauge(
        "executors_deployed_by_identity",
        "Deployed executors broken down by catalog identity (slug, managed, type)",
        () -> collectInventory(ConnectorType.EXECUTOR, executorRepository.findAll()));
  }

  private Map<Attributes, Long> collectInventory(
      ConnectorType connectorType, Iterable<? extends BaseConnectorEntity> entities) {
    Map<Attributes, Long> inventory = new HashMap<>();
    try {
      Map<String, CatalogConnector> catalogByConnectorId = mapCatalogByConnectorId(connectorType);
      for (BaseConnectorEntity entity : entities) {
        CatalogConnector catalogConnector = catalogByConnectorId.get(entity.getId());
        boolean managed = catalogConnector != null;
        String slug = managed ? catalogConnector.getSlug() : entity.getType();
        if (slug == null || slug.isBlank()) {
          continue;
        }
        Attributes attributes =
            Attributes.of(
                stringKey(ATTRIBUTE_SLUG), slug.trim().toLowerCase(),
                stringKey(ATTRIBUTE_MANAGED), String.valueOf(managed),
                stringKey(ATTRIBUTE_TYPE), entity.getType() == null ? "" : entity.getType());
        inventory.merge(attributes, 1L, Long::sum);
      }
    } catch (Exception e) {
      log.error("Telemetry - Failed to collect connector inventory metrics", e);
    }
    return inventory;
  }

  /**
   * Maps each registered connector id to its catalog connector through the catalog-deployed
   * connector instances, joined via the {@code INJECTOR_ID} / {@code COLLECTOR_ID} / {@code
   * EXECUTOR_ID} instance-configuration key.
   */
  private Map<String, CatalogConnector> mapCatalogByConnectorId(ConnectorType connectorType) {
    Map<String, CatalogConnector> catalogByConnectorId = new HashMap<>();
    List<ConnectorInstancePersisted> instances =
        connectorInstanceRepository.findAllByCatalogConnectorContainerType(connectorType);
    for (ConnectorInstancePersisted instance : instances) {
      if (instance.getCatalogConnector() == null) {
        continue;
      }
      instance.getConfigurations().stream()
          .filter(configuration -> connectorType.getIdKeyName().equals(configuration.getKey()))
          .map(configuration -> configuration.getValue().asText())
          .filter(connectorId -> connectorId != null && !connectorId.isBlank())
          .findFirst()
          .ifPresent(
              connectorId -> catalogByConnectorId.put(connectorId, instance.getCatalogConnector()));
    }
    return catalogByConnectorId;
  }
}
