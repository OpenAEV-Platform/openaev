package io.openaev.utils.fixtures;

import static io.openaev.database.model.Scenario.SEVERITY.high;

import io.openaev.rest.scenario.form.ScenarioInput;
import java.util.List;

public final class ScenarioInputFixture {

  private ScenarioInputFixture() {}

  public static ScenarioInput createAuditScenarioInput(String scenarioName) {
    ScenarioInput scenarioInput = new ScenarioInput();
    scenarioInput.setName(scenarioName);
    scenarioInput.setCategory("attack-scenario");
    scenarioInput.setMainFocus("incident-response");
    scenarioInput.setSeverity(high);
    scenarioInput.setSubtitle("");
    scenarioInput.setDescription("");
    scenarioInput.setTagIds(List.of());
    scenarioInput.setExternalReference("");
    scenarioInput.setExternalUrl("");
    scenarioInput.setReplyTos(List.of("openaev-dev@test.io"));
    scenarioInput.setHeader("SIMULATION HEADER");
    scenarioInput.setFooter("SIMULATION FOOTER");
    return scenarioInput;
  }
}
