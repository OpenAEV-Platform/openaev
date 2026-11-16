package io.openaev.service.catalog.scenario;

import io.openaev.database.model.Payload;
import io.openaev.database.model.Scenario;
import java.util.stream.Stream;

/** What an install does that depends on the type of the scenario. */
public interface CatalogScenarioTypeInstall<C extends ScenarioInstallChoice> {

  CatalogScenarioType type();

  /** What an install with no choice applies: by itself, or by hand without a body. */
  C noChoice();

  /** The payloads the imported scenario runs, each checked against its release. */
  Stream<Payload> payloadsRun(Scenario scenario) throws Exception;

  void apply(Scenario scenario, C choice) throws Exception;
}
