package io.openaev.api.users.dto;

import static io.openaev.config.AppConfig.EMAIL_FORMAT;
import static io.openaev.config.AppConfig.PHONE_FORMAT;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;

public record UserInput(
    @JsonProperty("user_email") @NotBlank @Email(message = EMAIL_FORMAT) String email,
    @JsonProperty("user_firstname") String firstname,
    @JsonProperty("user_lastname") String lastname,
    @JsonProperty("user_pgp_key") String pgpKey,
    @JsonProperty("user_phone") @Pattern(regexp = "^$|^\\+[\\d\\s\\-.()]+$", message = PHONE_FORMAT)
        String phone,
    @JsonProperty("user_phone2")
        @Pattern(regexp = "^$|^\\+[\\d\\s\\-.()]+$", message = PHONE_FORMAT)
        String phone2,
    @JsonProperty("user_organization") String organizationId,
    @JsonProperty("user_plain_password") String plainPassword,
    @JsonProperty("user_tags") List<String> tagIds) {}
