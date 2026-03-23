package io.openaev.service;

import io.openaev.multitenancy.DependenciesManager;

public class RabbitmqService implements DependenciesManager {

  @Override
  public void createDependencyForTenant(String tenantId) {
    // No action needed, the queues are shared between tenants
  }

  @Override
  public void deleteDependencyForTenant(String tenantId) {
    // No action needed, the queues are shared between tenants
  }

}
