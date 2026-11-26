package io.openaev.database.raw;

/** A scenario of a tenant installed from a catalogue item. */
public interface RawCatalogInstalledScenario {

  String getRepository();

  String getExternalId();

  String getScenarioId();
}
