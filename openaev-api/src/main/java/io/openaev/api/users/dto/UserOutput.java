package io.openaev.api.users.dto;

import static io.openaev.config.AppConfig.EMAIL_FORMAT;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;

public record UserOutput(
    @JsonProperty("user_id") @NotBlank String id,
    @JsonProperty("user_email") @NotBlank @Email(message = EMAIL_FORMAT) String email,
    @JsonProperty("user_firstname") String firstname,
    @JsonProperty("user_lastname") String lastname,
    @JsonProperty("user_phone") String phone,
    @JsonProperty("user_phone2") String phone2,
    @JsonProperty("user_organization_id") String organizationId,
    @JsonProperty("user_organization_name") String organizationName,
    @JsonProperty("user_tags") Set<String> tagIds,
    @JsonProperty("user_has_password") boolean hasPassword,
    @JsonProperty("user_has_pgp_key") boolean hasPgpKey) {}
