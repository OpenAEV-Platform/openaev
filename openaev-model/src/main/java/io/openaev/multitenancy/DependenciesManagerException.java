package io.openaev.multitenancy;

public class DependenciesManagerException extends Exception {

  public DependenciesManagerException(String errorMessage) {
    super(errorMessage);
  }

  public DependenciesManagerException(String errorMessage, Exception cause) {
    super(errorMessage, cause);
  }
}
