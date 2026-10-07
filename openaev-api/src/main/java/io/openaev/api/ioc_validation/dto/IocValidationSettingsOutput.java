package io.openaev.api.ioc_validation.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.IocValidationTestKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** The tenant IOC validation safety settings and the state of the OpenCTI connection. */
public record IocValidationSettingsOutput(
    @JsonProperty("ioc_validation_allowed_test_kinds")
        @Schema(description = "Test kinds the tenant allows (default: none)")
        @NotNull
        List<IocValidationTestKind> allowedTestKinds,
    @JsonProperty("ioc_validation_http_proxy_url")
        @Schema(description = "Egress proxy URL for HTTP HEAD tests (required to allow HTTP_HEAD)")
        String httpProxyUrl,
    @JsonProperty("ioc_validation_sinkhole_address")
        @Schema(
            description =
                "Sinkhole IP: when set, network tests connect to it instead of the IOC address")
        String sinkholeAddress,
    @JsonProperty("ioc_validation_network_port")
        @Schema(
            description = "TCP port used by network tests (default 443)",
            requiredMode = REQUIRED)
        int networkPort,
    @JsonProperty("ioc_validation_asset_group_id")
        @Schema(description = "Default asset group targeted by validation injects")
        String assetGroupId,
    @JsonProperty("ioc_validation_opencti_enabled")
        @Schema(
            description = "Whether an OpenCTI connection is configured for this tenant",
            requiredMode = REQUIRED)
        boolean openctiEnabled,
    @JsonProperty("ioc_validation_connector_registered")
        @Schema(
            description = "Whether the OpenAEV IOC validation connector is registered in OpenCTI",
            requiredMode = REQUIRED)
        boolean connectorRegistered) {}
