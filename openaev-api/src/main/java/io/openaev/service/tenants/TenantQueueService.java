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
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Manages tenant-scoped RabbitMQ queues lifecycle.
 *
 * <p>At startup, declares all configured queues for every active tenant. On tenant creation,
 * declares queues for the new tenant. On tenant deletion (purge), deletes the tenant's queues.
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

  /** Declares all configured queues for a newly created tenant. */
  @Override
  public void createDependencyForTenant(Tenant tenant) throws DependenciesManagerException {
    List<QueueConfig> configs = getQueueConfigs();
    if (configs.isEmpty()) {
      return;
    }
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

  // -- INTERNAL --

  /** Returns the list of all configured queue configs (inject-trace, workflows-ready, etc.). */
  private List<QueueConfig> getQueueConfigs() {
    if (openAEVConfig.getQueueConfig() == null) {
      return List.of();
    }
    return new ArrayList<>(openAEVConfig.getQueueConfig().values());
  }
}
