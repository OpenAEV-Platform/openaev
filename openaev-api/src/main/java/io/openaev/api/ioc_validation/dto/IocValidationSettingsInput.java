package io.openaev.api.ioc_validation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.IocValidationTestKind;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** The tenant IOC validation safety settings, as edited in Settings > Customization. */
public record IocValidationSettingsInput(
    @JsonProperty("ioc_validation_allowed_test_kinds") @NotNull
        List<@NotNull IocValidationTestKind> allowedTestKinds,
    @JsonProperty("ioc_validation_http_proxy_url") @Size(max = 2048) String httpProxyUrl,
    @JsonProperty("ioc_validation_sinkhole_address") @Size(max = 64) String sinkholeAddress,
    @JsonProperty("ioc_validation_network_port") @NotNull @Min(1) @Max(65535) Integer networkPort,
    @JsonProperty("ioc_validation_asset_group_id") @Size(max = 255) String assetGroupId) {}
