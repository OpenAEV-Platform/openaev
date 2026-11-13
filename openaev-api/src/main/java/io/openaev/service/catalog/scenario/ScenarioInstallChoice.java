package io.openaev.service.catalog.scenario;

/** What an admin chooses at install, for one type of scenario. */
public interface ScenarioInstallChoice {

  /** The type of scenario this choice is for. */
  CatalogScenarioType type();
}
