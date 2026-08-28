package io.openaev.api.threat_arsenal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.BaseInjectExpectation;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One predefined expectation declared by a threat-arsenal action's injector contract, carrying
 * enough to describe it in the "Action information" drawer without another round-trip.
 *
 * <p>Unlike {@code action_expectations} (a bare list of types), this keeps the contract-declared
 * name, description and display order. It is what lets the drawer render a phishing action's human
 * steps as their real, ordered outcomes ("Email not opened" {@literal ->} "Link not clicked"
 * {@literal ->} "Credentials not submitted") instead of three indistinguishable "Manual" rows.
 * {@code name} / {@code description} / {@code order} are null for expectations a contract declares
 * by type only (most technical detection/prevention), and the reader falls back to the type label.
 */
public record ThreatArsenalExpectationDetail(
    @Schema(description = "Expectation type") @JsonProperty("expectation_type")
        BaseInjectExpectation.EXPECTATION_TYPE type,
    @Schema(description = "Contract-declared expectation name (null = unnamed, use the type label)")
        @JsonProperty("expectation_name")
        String name,
    @Schema(description = "Contract-declared expectation description (null = none)")
        @JsonProperty("expectation_description")
        String description,
    @Schema(
            description =
                "Contract-declared display order, ascending (e.g. phishing orders its steps email"
                    + " -> link -> submission); null = unordered")
        @JsonProperty("expectation_order")
        Integer order) {}
