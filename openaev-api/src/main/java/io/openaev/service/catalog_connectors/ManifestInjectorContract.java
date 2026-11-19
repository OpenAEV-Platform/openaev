package io.openaev.service.catalog_connectors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/** A contract of a catalogue injector, in the shape the injector registers it (pyoaev). */
@JsonIgnoreProperties(ignoreUnknown = true)
record ManifestInjectorContract(
    @JsonProperty("contract_id") String id,
    @JsonProperty("contract_labels") Map<String, String> labels,
    @JsonProperty("contract_domains") List<ManifestDomain> domains,
    @JsonProperty("contract_platforms") List<String> platforms,
    @JsonProperty("contract_attack_patterns_external_ids") List<String> attackPatterns) {

  // A key the injector leaves out reads as empty.
  ManifestInjectorContract {
    labels = labels == null ? Map.of() : labels;
    domains = domains == null ? List.of() : domains;
    platforms = platforms == null ? List.of() : platforms;
    attackPatterns = attackPatterns == null ? List.of() : attackPatterns;
  }

  List<String> domainNames() {
    return domains.stream().map(ManifestDomain::name).toList();
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record ManifestDomain(@JsonProperty("domain_name") String name) {}
}
