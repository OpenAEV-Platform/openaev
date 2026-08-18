package io.openaev.ocsf.parser.generator.emission;

public record ClassMetadata(String ocsfIdentifier, String className, String classPackage) {

  public String fullyQualifiedClassName() {
    return String.join(".", classPackage(), className());
  }
}
