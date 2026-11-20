package io.openaev.rest.settings.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** A runtime setting as Settings &gt; Parameters shows it: its value in force and its source. */
@Schema(description = "A platform setting an administrator may change unless its env var is set")
public record RuntimeSettingOutput(
    @NotBlank @JsonProperty("key") @Schema(description = "Setting key") String key,
    @NotBlank @JsonProperty("value") @Schema(description = "Value in force, as text") String value,
    @NotBlank
        @JsonProperty("source")
        @Schema(
            description =
                "Where the value comes from: env (environment variable, read-only), ui (set by an"
                    + " administrator) or default (configuration default)",
            allowableValues = {"env", "ui", "default"})
        String source,
    @NotBlank
        @JsonProperty("env_var")
        @Schema(description = "Environment variable that pins the setting when set")
        String envVar,
    @NotNull
        @JsonProperty("locked")
        @Schema(description = "True when the environment variable is set: the UI is read-only")
        Boolean locked,
    @NotBlank
        @JsonProperty("type")
        @Schema(
            description = "Value type",
            allowableValues = {"integer", "boolean"})
        String type,
    @JsonProperty("min") @Schema(description = "Lowest value, for an integer") Integer min,
    @JsonProperty("max") @Schema(description = "Highest value, for an integer") Integer max,
    @NotNull
        @JsonProperty("enterprise_only")
        @Schema(description = "Only effective with an Enterprise Edition license")
        Boolean enterpriseOnly) {}
