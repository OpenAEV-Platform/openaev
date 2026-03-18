package io.openaev.api.users.dto;

import static io.openaev.config.AppConfig.EMAIL_FORMAT;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UserInput(
    @JsonProperty("user_email") @NotBlank @Email(message = EMAIL_FORMAT) String email,
    @JsonProperty("user_plain_password") @NotBlank String plainPassword,
    @JsonProperty("user_pgp_key") String pgpKey) {}
