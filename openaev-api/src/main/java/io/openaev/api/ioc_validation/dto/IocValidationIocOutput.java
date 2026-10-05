package io.openaev.api.ioc_validation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.IocValidationTestKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/** One IOC of an IOC validation, with the test that runs for it or why it is skipped. */
public record IocValidationIocOutput(
    @JsonProperty("ioc_indicator_ref")
        @Schema(description = "STIX id of the OpenCTI indicator")
        @NotBlank
        String indicatorRef,
    @JsonProperty("ioc_indicator_name") @Schema(description = "Indicator name")
        String indicatorName,
    @JsonProperty("ioc_observable_type")
        @Schema(
            description =
                "OpenCTI observable type (Domain-Name, Hostname, IPv4-Addr, IPv6-Addr, Url, StixFile)")
        @NotBlank
        String observableType,
    @JsonProperty("ioc_value") @Schema(description = "Observable value") @NotBlank String value,
    @JsonProperty("ioc_requested_test_kind") @Schema(description = "Test kind requested by OpenCTI")
        IocValidationTestKind requestedTestKind,
    @JsonProperty("ioc_test_kind")
        @Schema(description = "Test kind OpenAEV actually runs (absent when the IOC is skipped)")
        IocValidationTestKind testKind,
    @JsonProperty("ioc_file_name") @Schema(description = "File name of a StixFile IOC")
        String fileName,
    @JsonProperty("ioc_hashes")
        @Schema(description = "Hashes of a StixFile IOC (algorithm -> value)")
        Map<String, String> hashes,
    @JsonProperty("ioc_inject_ids")
        @Schema(description = "Ids of the benign injects built for this IOC")
        @NotNull
        List<String> injectIds,
    @JsonProperty("ioc_message")
        @Schema(description = "Why the IOC was skipped or how it was adapted (sinkhole...)")
        String message,
    @JsonProperty("ioc_refused")
        @Schema(
            description =
                "Whether the IOC value was refused by the value checks (characters outside the"
                    + " accepted set, internal address...); the message says why")
        boolean refused) {}
