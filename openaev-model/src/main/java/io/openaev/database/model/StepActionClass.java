package io.openaev.database.model;

public enum StepActionClass {
  INJECT_EXECUTION("InjectExecutionStep.class");

  public final String className;

  StepActionClass(String className) {
    this.className = className;
  }
}
