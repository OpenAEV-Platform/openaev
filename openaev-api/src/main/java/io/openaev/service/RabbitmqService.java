package io.openaev.service;

import io.openaev.multitenancy.DependenciesManager;

public class RabbitmqService implements DependenciesManager {

  /*
  Rapatrier tous les bouts de code qui concernent RabbitMQ, notamment la création des queues, les listeners, etc. dans ce service. (gestion des connexions également)
   */

  @Override
  public void createDependencyForTenant(String tenantId) {
    // No action needed, the queues are shared between tenants
  }

  @Override
  public void deleteDependencyForTenant(String tenantId) {
    // No action needed, the queues are shared between tenants
  }

}
