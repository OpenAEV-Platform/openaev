package io.openaev.database.model;

/**
 * Where an asset link or an asset owner comes from (#9725). A manual entry always wins: an
 * automatic source refreshes a manual link without taking it over, and a manual removal of an
 * automatic link is kept, so the next report does not bring it back.
 */
public enum AssetRelationSource {
  /** Added by a person, from the UI or the API. */
  MANUAL,
  /** Reported by a collector. */
  COLLECTOR,
  /** Written by a synchronisation with another product. */
  SYNC,
  /** Reported by an OpenAEV agent (the account it runs as). */
  AGENT;

  public boolean isAutomatic() {
    return this != MANUAL;
  }
}
