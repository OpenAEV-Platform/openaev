package io.openaev.processor.core;

import io.openaev.config.OpenAEVConfig;
import io.openaev.config.QueueConfig;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Injector;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.service.DataPackService;
import io.openaev.service.RabbitmqService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * One-shot migration that cleans up legacy RabbitMQ queues and exchanges.
 *
 * <p>For each legacy prefix ({@code openbas_} and, if applicable, old-format {@code openaev_}
 * queues), the migration:
 *
 * <ol>
 *   <li>Lists all queues with that prefix via the management API
 *   <li>If a queue is empty, deletes it immediately
 *   <li>If a queue has messages, tries to identify the correct new queue to transfer them to
 *   <li>If no matching new queue is found, logs a warning for manual intervention
 * </ol>
 *
 * <p>The same logic applies to queues with the current prefix ({@code openaev}) that do not match
 * the expected tenant-scoped naming scheme.
 *
 * <p>This migration only runs for the default tenant because the old queues were not tenant-scoped.
 */
@Component
@Slf4j
public class V20260420_Migrate_rabbitmq_queues extends RuntimeMigration {

  private static final String LEGACY_PREFIX = "openbas";

  private final RabbitmqService rabbitmqService;
  private final InjectorRepository injectorRepository;
  private final Map<String, QueueConfig> queueConfigs;

  public V20260420_Migrate_rabbitmq_queues(
      DataPackService dataPackService,
      RabbitmqService rabbitmqService,
      InjectorRepository injectorRepository,
      OpenAEVConfig openAEVConfig) {
    super(dataPackService);
    this.rabbitmqService = rabbitmqService;
    this.injectorRepository = injectorRepository;
    this.queueConfigs =
        openAEVConfig.getQueueConfig() != null ? openAEVConfig.getQueueConfig() : Map.of();
  }

  @Override
  protected boolean doMigrate() {
    String tenantId = TenantContext.getCurrentTenant();

    // Only migrate for the default tenant — old queues were not tenant-scoped
    if (!Tenant.DEFAULT_TENANT_UUID.equals(tenantId)) {
      log.info("Skipping RabbitMQ queue migration for non-default tenant {}.", tenantId);
      return true;
    }

    String currentPrefix = rabbitmqService.getPrefix();
    String tenantPrefix = currentPrefix + "-" + tenantId;

    // Build the set of expected queue names under the current prefix
    Set<String> expectedQueues = buildExpectedQueueNames(currentPrefix, tenantPrefix);
    Set<String> expectedExchanges = buildExpectedExchangeNames(currentPrefix, tenantPrefix);

    // Build a mapping from old queue name patterns to their new target queue
    Map<String, String> injectorMap = buildInjectorIdAndTypeMap();

    try {
      // 1. Clean up all queues/exchanges with the legacy "openbas" prefix
      if (!LEGACY_PREFIX.equals(currentPrefix)) {
        migrateQueuesWithPrefix(LEGACY_PREFIX + "_", currentPrefix, tenantPrefix, injectorMap);
        cleanupExchangesWithPrefix(LEGACY_PREFIX + "_", expectedExchanges);
      }

      // 2. Clean up queues/exchanges with the current prefix that don't match the expected schema
      migrateNonConformingQueues(currentPrefix, expectedQueues, tenantPrefix, injectorMap);
      cleanupNonConformingExchanges(currentPrefix, expectedExchanges);
    } catch (Exception e) {
      log.error("RabbitMQ queue migration failed.", e);
      return false;
    }

    return true;
  }

  /**
   * Lists all queues with the given prefix, and for each one: deletes it if empty, transfers
   * messages if a target can be found, or logs a warning.
   */
  private void migrateQueuesWithPrefix(
      String prefix, String currentPrefix, String tenantPrefix, Map<String, String> injectorMap) {
    List<String> queues = rabbitmqService.listQueueNamesWithPrefix(prefix);
    for (String queue : queues) {
      processLegacyQueue(queue, prefix, currentPrefix, tenantPrefix, injectorMap);
    }
  }

  /**
   * Lists all queues with the current prefix and processes those that do not match the expected
   * naming scheme.
   */
  private void migrateNonConformingQueues(
      String currentPrefix,
      Set<String> expectedQueues,
      String tenantPrefix,
      Map<String, String> injectorMap) {
    List<String> queues = rabbitmqService.listQueueNamesWithPrefix(currentPrefix + "_");
    for (String queue : queues) {
      if (!expectedQueues.contains(queue)) {
        processLegacyQueue(queue, currentPrefix + "_", currentPrefix, tenantPrefix, injectorMap);
      }
    }
  }

  /**
   * Processes a single legacy queue: resolves the target first, then drains and transfers messages.
   * If no target can be found, the queue is left untouched to avoid data loss.
   */
  private void processLegacyQueue(
      String queue,
      String oldPrefix,
      String currentPrefix,
      String tenantPrefix,
      Map<String, String> injectorMap) {
    try {
      // Resolve target BEFORE draining to avoid data loss if no match is found
      QueueTarget target =
          resolveTarget(queue, oldPrefix, currentPrefix, tenantPrefix, injectorMap);

      if (target == null) {
        log.warn(
            "Could not determine target for legacy queue '{}'. "
                + "The queue was NOT modified — it needs to be handled manually.",
            queue);
        return;
      }

      List<byte[]> messages = rabbitmqService.drainQueue(queue);

      if (messages.isEmpty()) {
        log.info("Legacy queue '{}' is empty — deleting.", queue);
        rabbitmqService.safeDeleteQueue(queue);
        return;
      }

      // Ensure target exchange exists before publishing — bean init order is not guaranteed,
      // so the exchange may not have been declared yet. This is idempotent.
      rabbitmqService.ensureExchangeExists(target.exchange, target.exchangeType);
      rabbitmqService.publishBatch(target.exchange, target.routingKey, messages);
      log.info(
          "Migrated {} messages from '{}' to exchange '{}' (routing key '{}').",
          messages.size(),
          queue,
          target.exchange,
          target.routingKey);
      rabbitmqService.safeDeleteQueue(queue);
    } catch (Exception e) {
      log.warn(
          "Failed to process legacy queue '{}': {}. It may need to be handled manually.",
          queue,
          e.getMessage());
    }
  }

  /**
   * Attempts to resolve the new exchange and routing key for a legacy queue name.
   *
   * <p>Recognizes two patterns:
   *
   * <ul>
   *   <li>{@code {oldPrefix}execution_{name}} → batch queue → {@code
   *       {tenantPrefix}_execution_{name}}
   *   <li>{@code {oldPrefix}injector_{type}} → injector queue → {@code
   *       {currentPrefix}_injector_{id}}
   * </ul>
   *
   * @return a {@link QueueTarget} if a match is found, or {@code null}
   */
  private QueueTarget resolveTarget(
      String queue,
      String oldPrefix,
      String currentPrefix,
      String tenantPrefix,
      Map<String, String> injectorMap) {

    String suffix = queue.startsWith(oldPrefix) ? queue.substring(oldPrefix.length()) : queue;

    // Pattern: execution_{name}
    if (suffix.startsWith("execution_")) {
      String name = suffix.substring("execution_".length());
      // Check that this is a known queue config name
      boolean knownQueue =
          queueConfigs.values().stream().anyMatch(c -> c.getQueueName().equals(name));
      if (knownQueue) {
        String newExchange = tenantPrefix + "_amqp." + name + ".exchange";
        String newRoutingKey = tenantPrefix + RabbitmqService.ROUTING_KEY + name;
        return new QueueTarget(newExchange, "topic", newRoutingKey);
      }
    }

    // Pattern: injector_{typeOrId}
    if (suffix.startsWith("injector_")) {
      String typeOrId = suffix.substring("injector_".length());
      String injectorId = injectorMap.getOrDefault(typeOrId, null);
      if (injectorId != null) {
        String newExchange = currentPrefix + RabbitmqService.EXCHANGE_KEY;
        String newRoutingKey = currentPrefix + RabbitmqService.ROUTING_KEY + injectorId;
        return new QueueTarget(newExchange, "direct", newRoutingKey);
      }
    }

    return null;
  }

  /** Deletes all exchanges with the given prefix. */
  private void cleanupExchangesWithPrefix(String prefix, Set<String> protectedExchanges) {
    List<String> exchanges = rabbitmqService.listExchangeNamesWithPrefix(prefix);
    for (String exchange : exchanges) {
      if (!protectedExchanges.contains(exchange)) {
        rabbitmqService.safeDeleteExchange(exchange);
      }
    }
  }

  /** Deletes exchanges with the current prefix that do not match the expected naming scheme. */
  private void cleanupNonConformingExchanges(String currentPrefix, Set<String> expectedExchanges) {
    List<String> exchanges = rabbitmqService.listExchangeNamesWithPrefix(currentPrefix + "_");
    for (String exchange : exchanges) {
      if (!expectedExchanges.contains(exchange)) {
        log.info("Deleting non-conforming exchange '{}'.", exchange);
        rabbitmqService.safeDeleteExchange(exchange);
      }
    }
  }

  // -- HELPERS --

  /**
   * Builds a map that resolves both injector IDs and injector types to the canonical injector ID.
   *
   * <p>This allows matching legacy queues whether they used the injector ID or the injector type as
   * suffix. ID mappings take priority (added first).
   */
  private Map<String, String> buildInjectorIdAndTypeMap() {
    Map<String, String> map = new HashMap<>();
    for (Injector injector : injectorRepository.findAll()) {
      // id → id (identity mapping for queues that already use the correct id)
      map.putIfAbsent(injector.getId(), injector.getId());
      // type → id (fallback for queues that used the type instead)
      map.putIfAbsent(injector.getType(), injector.getId());
    }
    return map;
  }

  /** Returns the set of queue names that are expected under the current configuration. */
  private Set<String> buildExpectedQueueNames(String currentPrefix, String tenantPrefix) {
    Set<String> expected =
        queueConfigs.values().stream()
            .map(c -> tenantPrefix + "_execution_" + c.getQueueName())
            .collect(Collectors.toSet());

    // Injector queues: {prefix}_injector_{id}
    for (Injector injector : injectorRepository.findAll()) {
      expected.add(currentPrefix + "_injector_" + injector.getId());
    }

    return expected;
  }

  /** Returns the set of exchange names that are expected under the current configuration. */
  private Set<String> buildExpectedExchangeNames(String currentPrefix, String tenantPrefix) {
    Set<String> expected =
        queueConfigs.values().stream()
            .map(c -> tenantPrefix + "_amqp." + c.getQueueName() + ".exchange")
            .collect(Collectors.toSet());

    // Connector exchanges
    expected.add(currentPrefix + RabbitmqService.EXCHANGE_KEY);
    expected.add(tenantPrefix + RabbitmqService.EXCHANGE_KEY);

    return expected;
  }

  /** Simple holder for a target exchange, its type, and routing key. */
  private record QueueTarget(String exchange, String exchangeType, String routingKey) {}
}
