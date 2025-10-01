package io.openaev.importer;

public class ImportException extends RuntimeException {

  public ImportException(Throwable cause) {
    super(cause);
  }

  public ImportException(String message) {
    super(message);
  }
}
