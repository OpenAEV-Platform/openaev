package io.openaev.multitenancy;

public class MinioException extends Exception {

  public MinioException(String errorMessage) {
    super(errorMessage);
  }

  public MinioException(String errorMessage, Exception cause) {
    super(errorMessage, cause);
  }
}
