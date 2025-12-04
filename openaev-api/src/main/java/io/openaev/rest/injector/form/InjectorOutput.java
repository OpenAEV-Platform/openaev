package io.openaev.rest.injector.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.rest.catalog_connector.dto.CatalogConnectorSimpleOutput;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
@Schema(description = "Injector output")
public class InjectorOutput {
    @Schema(description = "Injector id")
    @JsonProperty("injector_id")
    @NotBlank
    private String id;

    @JsonProperty("injector_name")
    @NotBlank
    private String name;

    @JsonProperty("injector_type")
    @NotBlank
    private String type;

    @JsonProperty("injector_external")
    private boolean external = false;

    // TODO check need category, customContracts, executorCommands, executorClearCommands, payloads...
    @JsonProperty("catalog")
    private CatalogConnectorSimpleOutput catalog;

    @JsonProperty("is_verified")
    private boolean verified = false;

    @JsonProperty("injector_updated_at")
    private Instant updatedAt;
}
