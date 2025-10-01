package io.openaev.rest.scenario.response;

import io.openaev.database.model.Scenario;
import io.openaev.rest.challenge.output.PublicEntity;

public class PublicScenario extends PublicEntity {

  public PublicScenario(Scenario scenario) {
    setId(scenario.getId());
    setName(scenario.getName());
    setDescription(scenario.getDescription());
  }
}
