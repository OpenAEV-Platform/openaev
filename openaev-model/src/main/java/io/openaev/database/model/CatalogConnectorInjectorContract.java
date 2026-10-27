package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/** A contract an injector of the integrations catalogue registers once it runs. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogConnectorInjectorContract(
    @JsonProperty("contract_id") String contractId,
    @JsonProperty("labels") Map<String, String> labels,
    @JsonProperty("domains") List<String> domains,
    @JsonProperty("platforms") List<String> platforms,
    @JsonProperty("attack_patterns") List<String> attackPatterns)
    implements Serializable {}
