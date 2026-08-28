package io.openaev.rest.injector_contract.output;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/**
 * Fixed-universe facet counts for the inject-contract picker sidebar (platforms, kill chain phases
 * and payload statuses), computed under the current filters. Complements the domain-counts and
 * author-counts endpoints so every sidebar facet can display live counts and grey out the
 * zero-count rows.
 */
public record InjectorContractFacetCountsOutput(
    @Schema(description = "Number of contracts per platform under the current filters")
        @JsonProperty("platforms")
        Map<String, Long> platforms,
    @Schema(
            description =
                "Number of contracts per kill chain phase id under the current filters, through the attack pattern relation")
        @JsonProperty("kill_chain_phases")
        Map<String, Long> killChainPhases,
    @Schema(description = "Number of contracts per payload status under the current filters")
        @JsonProperty("statuses")
        Map<String, Long> statuses) {}
