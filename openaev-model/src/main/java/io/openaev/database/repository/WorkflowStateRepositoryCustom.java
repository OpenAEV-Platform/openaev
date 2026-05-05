package io.openaev.database.repository;

public interface WorkflowStateRepositoryCustom {
  void addInput(Long id, String input);

  void addCorrelated(Long id, String correlated);

  void addHash(Long id, String hashExecution);
}
