package io.openaev.multitenancy;

import io.openaev.database.model.Tenant;
import org.springframework.context.annotation.Profile;

/** Interface to create and delete all the necessary elements at tenant creation/deletion */
@Profile("!test")
public interface DependenciesManager {

  void createDependencyForTenant(Tenant tenant) throws DependenciesManagerException;

  void deleteDependencyForTenant(String tenantId) throws DependenciesManagerException;
}
