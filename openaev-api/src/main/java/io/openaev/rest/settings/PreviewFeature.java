package io.openaev.rest.settings;

import lombok.Getter;

/*
 * Currently available feature flags.
 *
 * Each option here designates a feature that is disabled by default.
 * Adding any of these flags to the `enabled_dev_features` configuration
 * key will enable the corresponding feature.
 * Over time, features that are hidden by these flags will be enabled globally
 * and their corresponding flag will be removed from this enum.
 */
@Getter
public enum PreviewFeature {
  // Reserved for internal use.
  _RESERVED("_RESERVED"),
  FEATURE_FLAG_ALL("*"),
  STIX_SECURITY_COVERAGE_FOR_VULNERABILITIES("STIX_SECURITY_COVERAGE_FOR_VULNERABILITIES"),
  LEGACY_INGESTION_EXECUTION_TRACE("LEGACY_INGESTION_EXECUTION_TRACE"),
  MULTI_TENANCY("MULTI_TENANCY"),
  SENTINEL_ONE_EXECUTOR("SENTINEL_ONE_EXECUTOR"),
  PALO_ALTO_CORTEX_EXECUTOR("PALO_ALTO_CORTEX_EXECUTOR"),
  OPENAEV_TRIALS_XTMHUB("OPENAEV_TRIALS_XTMHUB"),
  INJECT_CHAINING("INJECT_CHAINING");

  private final String value;

  PreviewFeature(String value) {
    this.value = value;
  }

  public static PreviewFeature fromStringIgnoreCase(String str) {
    for (PreviewFeature feature : PreviewFeature.values()) {
      if (feature.getValue().equalsIgnoreCase(str)) {
        return feature;
      }
    }
    throw new IllegalArgumentException("No preview feature found with name " + str);
  }
}
