package io.openaev.utils.fixtures;

import io.openaev.database.raw.RawScenarioUsageCount;

public class RawScenarioUsageCountFixture {

  public static RawScenarioUsageCount scenarioUsages(String id, String name, long count) {
    return new RawScenarioUsageCount() {
      @Override
      public String getScenarioId() {
        return id;
      }

      @Override
      public String getScenarioName() {
        return name;
      }

      @Override
      public long getUsageCount() {
        return count;
      }
    };
  }
}
