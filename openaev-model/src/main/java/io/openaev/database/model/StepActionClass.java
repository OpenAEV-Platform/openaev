package io.openaev.database.model;

public enum StepActionClass {
  //  TODO: Replace String with Class<?> once InjectExecutionStep is implemented

  INJECT_EXECUTION("InjectExecutionStep.class"),
  UNSUPPORTED("");

  public final String className;

  StepActionClass(String className) {
    this.className = className;
  }
}
