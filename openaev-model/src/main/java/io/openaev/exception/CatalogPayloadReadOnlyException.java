package io.openaev.exception;

/** A payload version released by the catalogue is never changed nor deleted on the platform. */
public class CatalogPayloadReadOnlyException extends ActionRefusedException {

  private CatalogPayloadReadOnlyException(String message) {
    super(message);
  }

  public static CatalogPayloadReadOnlyException onChange() {
    return new CatalogPayloadReadOnlyException(
        "This action comes from the Filigran catalogue: duplicate it to change it.");
  }

  public static CatalogPayloadReadOnlyException onDelete() {
    return new CatalogPayloadReadOnlyException(
        "This action comes from the Filigran catalogue: uninstall it instead.");
  }
}
