package io.openaev.ocsf.parser.schema;

public record Version(OcsfSchemaVersion versionNumber) {

  @Override
  public String toString() {
    return this.versionNumber().getValue();
  }
}
