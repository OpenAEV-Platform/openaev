package io.openaev.multitenancy;

/** Interface to create and delete all the necessary elements at tenant creation/deletion */
public interface DependenciesManager {

  void createDependencyForTenant(String uid) throws Exception;

  void deleteDependencyForTenant(String uid) throws Exception;
}
