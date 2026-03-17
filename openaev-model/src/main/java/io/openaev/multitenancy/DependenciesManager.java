package io.openaev.multitenancy;

import io.openaev.database.model.Tenant;

/** Interface to create and delete all the necessary elements at tenant creation/deletion */
public interface DependenciesManager {

  void createDependencyForTenant(Tenant tenant) throws DependenciesManagerException;

  void deleteDependencyForTenant(String tenantId) throws DependenciesManagerException;
}
