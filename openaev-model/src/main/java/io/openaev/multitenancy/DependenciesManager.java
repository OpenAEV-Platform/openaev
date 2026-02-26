package io.openaev.multitenancy;

/** Interface to create and delete all the necessary elements at tenant creation/deletion */
public interface DependenciesManager {

  void createDependency(String uid) throws Exception;

  void deleteDependency(String uid) throws Exception;
}
