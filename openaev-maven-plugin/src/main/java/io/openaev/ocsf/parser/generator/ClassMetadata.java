package io.openaev.ocsf.parser.generator;

import lombok.Getter;

@Getter
public class ClassMetadata {
  private final String ocsfIdentifier;
  private final String className;
  private final String classPackage;

  public ClassMetadata(String ocsfIdentifier, String className, String classPackage) {
    this.ocsfIdentifier = ocsfIdentifier;
    this.className = className;
    this.classPackage = classPackage;
  }

  public String fullyQualifiedClassName() {
    return String.join(".", getClassPackage(), getClassName());
  }
}
