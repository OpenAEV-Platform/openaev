package io.openaev.service.tenants;

import io.openaev.config.OpenAEVConfig;
import io.openaev.config.QueueConfig;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.TenantRepository;
import io.openaev.multitenancy.DependenciesManager;
import io.openaev.multitenancy.DependenciesManagerException;
import io.openaev.service.RabbitmqService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Manages tenant-scoped RabbitMQ queues lifecycle.
 *
 * <p>At startup, declares all configured queues for every active tenant. On tenant creation,
 * declares queues for the new tenant, and deletes them again if the creation rolls back, retrying
 * that deletion until it succeeds. On tenant deletion (purge), deletes the tenant's queues and
 * exchanges.
 *
 * <p>Queue name pattern per tenant: {@code prefix-tenantId_execution_queueName}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantQueueService implements DependenciesManager {

  private final RabbitmqService rabbitmqService;
  private final OpenAEVConfig openAEVConfig;
  private final TenantRepository tenantRepository;

  /**
   * Rolled back tenants whose broker objects could not be deleted. Held in memory: no tenant row is
   * left to find them by, and nothing written inside the rolled back transaction survives it.
   */
  private final Set<String> rollbackCleanupsToRetry = ConcurrentHashMap.newKeySet();

  // -- LIFECYCLE --

  /**
   * At application startup, declares RabbitMQ queues for every active tenant so that messages can
   * be routed immediately.
   */
  @PostConstruct
  public void init() {
    List<QueueConfig> configs = getQueueConfigs();
    if (configs.isEmpty()) {
      log.info("No queue configurations found — skipping tenant queue initialization.");
      return;
    }

    tenantRepository
        .findAll()
        .forEach(
            tenant -> {
              try {
                rabbitmqService.declareQueuesForTenant(tenant.getId(), configs);
                log.info("Declared RabbitMQ queues for existing tenant '{}'", tenant.getId());
              } catch (IOException | TimeoutException e) {
                log.error("Failed to declare queues for tenant '{}' at startup", tenant.getId(), e);
              }
            });
  }

  /**
   * At shutdown, logs that tenant queues are being released. The queues are durable and survive
   * broker restarts — no explicit cleanup is needed.
   */
  @PreDestroy
  public void destroy() {
    log.info("Application shutting down — durable tenant queues remain on the broker.");
  }

  // -- DEPENDENCIES MANAGER --

  /**
   * Declares all configured queues for a newly created tenant.
   *
   * <p>The broker is not transactional, and no tenant row is left for the purge to find the objects
   * by if the onboarding transaction rolls back. So once a rollback is confirmed, the objects
   * declared so far are deleted. If that deletion fails, the tenant id is kept and {@link
   * #retryFailedRollbackCleanups} deletes them once the broker accepts it. The pending ids live in
   * this process only: they survive a broker outage, not a restart of the application, and objects
   * whose deletion is still pending when the application stops stay on the broker. When the
   * transaction outcome is unknown, the objects are kept, since the tenant row may exist.
   */
  @Override
  public void createDependencyForTenant(Tenant tenant) throws DependenciesManagerException {
    List<QueueConfig> configs = getQueueConfigs();
    if (configs.isEmpty()) {
      return;
    }
    // Registered before declaring, so a declaration that fails halfway is removed too.
    deleteQueuesOnRollback(tenant.getId(), configs);
    try {
      rabbitmqService.declareQueuesForTenant(tenant.getId(), configs);
      log.info("Created all RabbitMQ queues for tenant '{}'", tenant.getId());
    } catch (IOException | TimeoutException e) {
      throw new DependenciesManagerException(
          "Failed to create RabbitMQ queues for tenant " + tenant.getId(), e);
    }
  }

  /** Deletes all configured queues for a tenant being purged. */
  @Override
  public void deleteDependencyForTenant(String tenantId) throws DependenciesManagerException {
    List<QueueConfig> configs = getQueueConfigs();
    if (configs.isEmpty()) {
      return;
    }
    try {
      rabbitmqService.deleteQueuesForTenant(tenantId, configs);
      log.info("Deleted all RabbitMQ queues for tenant '{}'", tenantId);
    } catch (IOException | TimeoutException e) {
      throw new DependenciesManagerException(
          "Failed to delete RabbitMQ queues for tenant " + tenantId, e);
    }
  }

  /**
   * Retries the deletion of the broker objects of rolled back tenants whose first attempt failed. A
   * tenant stays pending until its deletion succeeds; deleting objects that are already gone is a
   * no-op on the broker, so a deletion that failed partway is completed.
   */
  @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
  public void retryFailedRollbackCleanups() {
    for (String tenantId : List.copyOf(rollbackCleanupsToRetry)) {
      try {
        rabbitmqService.deleteQueuesForTenant(tenantId, getQueueConfigs());
        rollbackCleanupsToRetry.remove(tenantId);
        log.info("Deleted RabbitMQ queues of rolled back tenant '{}' on retry", tenantId);
      } catch (IOException | TimeoutException | RuntimeException e) {
        log.warn(
            "Retry failed to delete RabbitMQ queues of rolled back tenant '{}': {}",
            tenantId,
            e.getMessage());
      }
    }
  }

  // -- INTERNAL --

  private void deleteQueuesOnRollback(String tenantId, List<QueueConfig> configs) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            // Only a confirmed rollback: on an unknown outcome the tenant row may exist.
            if (status != STATUS_ROLLED_BACK) {
              return;
            }
            try {
              rabbitmqService.deleteQueuesForTenant(tenantId, configs);
              log.info("Deleted RabbitMQ queues of rolled back tenant '{}'", tenantId);
            } catch (IOException | TimeoutException | RuntimeException e) {
              rollbackCleanupsToRetry.add(tenantId);
              log.error(
                  "Failed to delete RabbitMQ queues of rolled back tenant '{}', will retry",
                  tenantId,
                  e);
            }
          }
        });
  }

  /** Returns the list of all configured queue configs (inject-trace, workflows-ready, etc.). */
  private List<QueueConfig> getQueueConfigs() {
    if (openAEVConfig.getQueueConfig() == null) {
      return List.of();
    }
    return new ArrayList<>(openAEVConfig.getQueueConfig().values());
  }
}
