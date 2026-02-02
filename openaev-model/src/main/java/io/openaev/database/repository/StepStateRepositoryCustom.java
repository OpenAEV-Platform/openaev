package io.openaev.database.repository;

public interface StepStateRepositoryCustom {
  void addInput(Long id, String input);

  void addCorrelated(Long id, String correlated);

  void addHash(Long id, Long hashExecution);
}
