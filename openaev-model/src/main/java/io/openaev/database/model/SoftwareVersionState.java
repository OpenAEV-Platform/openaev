package io.openaev.database.model;

/**
 * Whether the version of a software component is known. An SBOM that says NOASSERTION, an agent
 * that cannot read the version or an API call without one gives {@link #UNKNOWN}: a consumer must
 * treat it as "could be any version", never as "not affected".
 */
public enum SoftwareVersionState {
  KNOWN,
  UNKNOWN
}
