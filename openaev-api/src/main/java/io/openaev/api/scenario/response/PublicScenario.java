package io.openaev.api.scenario.response;

import io.openaev.api.challenge.output.PublicEntity;
import io.openaev.database.model.Scenario;

public class PublicScenario extends PublicEntity {

  public PublicScenario(Scenario scenario) {
    setId(scenario.getId());
    setName(scenario.getName());
    setDescription(scenario.getDescription());
  }
}
