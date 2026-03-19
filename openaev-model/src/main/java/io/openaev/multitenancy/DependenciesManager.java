package io.openaev.multitenancy;

/** Interface to create and delete all the necessary elements at tenant creation/deletion */
public interface DependenciesManager {

  void createDependencyForTenant(String tenantId) throws DependenciesManagerException;

  void deleteDependencyForTenant(String tenantId) throws DependenciesManagerException;
}
