package io.openaev.database.raw;

/** How many injects and chained steps of a scenario run some actions. */
public interface RawScenarioUsageCount {

  String getScenarioId();

  String getScenarioName();

  long getUsageCount();
}
