package io.openaev.telemetry.metric_collectors;

import static io.opentelemetry.api.common.AttributeKey.booleanKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.BaseConnectorEntity;
import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.ConnectorInstanceConfiguration;
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
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Telemetry inventory of the deployed injectors, collectors and executors, broken down by catalog
 * identity. Components deployed from the catalog (XTM Composer) resolve to their catalog connector
 * slug through the connector_instances join (the user-set label is irrelevant); manually deployed
 * components fall back to their own code-level type slug, flagged managed=false. Same resolution as
 * {@code AbstractConnectorService.getConnectorRelationsId()}. Anonymous only: identity slugs and
 * counts, never any component configuration.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryMetricCollector {

  private static final String ATTRIBUTE_SLUG = "slug";
  private static final String ATTRIBUTE_MANAGED = "managed";
  private static final String ATTRIBUTE_TYPE = "type";

  private final MetricRegistry metricRegistry;
  private final TenantScopedTransaction tenantTx;
  private final InjectorRepository injectorRepository;
  private final CollectorRepository collectorRepository;
  private final ExecutorRepository executorRepository;
  private final ConnectorInstanceRepository connectorInstanceRepository;

  @PostConstruct
  public void init() {
    metricRegistry.registerMultiGauge(
        "injectors_deployed_by_identity",
        "Deployed injectors broken down by catalog identity (slug, managed, type)",
        () -> collectInventory(ConnectorType.INJECTOR, injectorRepository::findAll));
    metricRegistry.registerMultiGauge(
        "collectors_deployed_by_identity",
        "Deployed collectors broken down by catalog identity (slug, managed, type)",
        () -> collectInventory(ConnectorType.COLLECTOR, collectorRepository::findAll));
    metricRegistry.registerMultiGauge(
        "executors_deployed_by_identity",
        "Deployed executors broken down by catalog identity (slug, managed, type)",
        () -> collectInventory(ConnectorType.EXECUTOR, executorRepository::findAll));
  }

  private Map<Attributes, Long> collectInventory(
      ConnectorType connectorType,
      Supplier<? extends Iterable<? extends BaseConnectorEntity>> entitiesSupplier) {
    try {
      return tenantTx.execute(
          TxCtx.allTenants(), () -> collectInventoryScoped(connectorType, entitiesSupplier));
    } catch (Exception e) {
      log.error("Telemetry - Failed to collect connector inventory metrics", e);
      return Map.of();
    }
  }

  private Map<Attributes, Long> collectInventoryScoped(
      ConnectorType connectorType,
      Supplier<? extends Iterable<? extends BaseConnectorEntity>> entitiesSupplier) {
    Map<Attributes, Long> inventory = new HashMap<>();
    Map<String, CatalogConnector> catalogByConnectorId = mapCatalogByConnectorId(connectorType);
    for (BaseConnectorEntity entity : entitiesSupplier.get()) {
      String type = entity.getType() == null ? "" : entity.getType().trim();
      if (type.isEmpty()) {
        continue;
      }
      CatalogConnector catalogConnector = catalogByConnectorId.get(entity.getId());
      boolean managed = catalogConnector != null;
      String slug = managed ? catalogConnector.getSlug() : type;
      if (slug == null || slug.isBlank()) {
        continue;
      }
      Attributes attributes =
          Attributes.of(
              stringKey(ATTRIBUTE_SLUG),
              slug.trim().toLowerCase(Locale.ROOT),
              booleanKey(ATTRIBUTE_MANAGED),
              managed,
              stringKey(ATTRIBUTE_TYPE),
              type);
      inventory.merge(attributes, 1L, Long::sum);
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
          .map(ConnectorInstanceConfiguration::getValue)
          // Only textual nodes are valid connector ids: asText() on a JSON null
          // yields the literal "null" and would shadow a later valid value.
          .filter(value -> value != null && value.isTextual())
          .map(JsonNode::asText)
          .filter(connectorId -> !connectorId.isBlank())
          .findFirst()
          .ifPresent(
              connectorId -> catalogByConnectorId.put(connectorId, instance.getCatalogConnector()));
    }
    return catalogByConnectorId;
  }
}
