package io.openaev.database.model;

/** Where a software component of an asset was learned. */
public enum SoftwareSource {
  /** Reported by the endpoint agent or an executor. */
  AGENT,
  /** Read from an imported CycloneDX or SPDX SBOM. */
  SBOM,
  /** Written through the API by a user or an integration. */
  API
}
