package io.openaev.rest.inject.form;

public enum InjectExecutionAction {
  prerequisite_check,
  prerequisite_execution,
  cleanup_execution,
  command_execution,
  dns_resolution,
  file_execution,
  file_drop,
  complete,
}
