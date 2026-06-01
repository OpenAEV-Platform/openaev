package io.openaev.multitenancy;

import io.openaev.database.model.Tenant;
import java.util.List;

/** Interface to create and delete all the necessary elements at tenant creation/deletion */
public interface DependenciesManager {

  void createDependencyForTenant(Tenant tenant) throws DependenciesManagerException;

  void deleteDependencyForTenant(String tenantId) throws DependenciesManagerException;

  default List<Class<? extends DependenciesManager>> getPrerequisite() {
    return List.of();
  }
}
