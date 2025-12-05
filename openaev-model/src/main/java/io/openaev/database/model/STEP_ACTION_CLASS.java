package io.openaev.database.model;

public enum STEP_ACTION_CLASS {
  INJECT_EXECUTION("InjectExecution.class");

  final String className;

  STEP_ACTION_CLASS(String className) {
    this.className = className;
  }
}
