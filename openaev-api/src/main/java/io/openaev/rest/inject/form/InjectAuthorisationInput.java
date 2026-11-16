package io.openaev.rest.inject.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record InjectAuthorisationInput(
    @NotBlank @JsonProperty("authorisation") String authorisation) {}
