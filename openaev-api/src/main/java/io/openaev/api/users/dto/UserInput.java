package io.openaev.api.users.dto;

import static io.openaev.api.users.dto.UserOutput.ALIAS_ADMIN;
import static io.openaev.api.users.dto.UserOutput.ALIAS_EMAIL;
import static io.openaev.api.users.dto.UserOutput.ALIAS_FIRSTNAME;
import static io.openaev.api.users.dto.UserOutput.ALIAS_LASTNAME;
import static io.openaev.api.users.dto.UserOutput.ALIAS_ORGANIZATION;
import static io.openaev.api.users.dto.UserOutput.ALIAS_PGP_KEY;
import static io.openaev.api.users.dto.UserOutput.ALIAS_PHONE;
import static io.openaev.api.users.dto.UserOutput.ALIAS_PHONE2;
import static io.openaev.api.users.dto.UserOutput.ALIAS_PLAIN_PASSWORD;
import static io.openaev.api.users.dto.UserOutput.ALIAS_TAGS;
import static io.openaev.api.users.dto.UserOutput.ALIAS_TENANTS;
import static io.openaev.config.AppConfig.EMAIL_FORMAT;
import static io.openaev.config.AppConfig.PHONE_FORMAT;
import static io.openaev.config.AppConfig.PHONE_REGEXP;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;

public record UserInput(
    @JsonProperty(ALIAS_EMAIL) @NotBlank @Email(message = EMAIL_FORMAT) String email,
    @JsonProperty(ALIAS_FIRSTNAME) String firstname,
    @JsonProperty(ALIAS_LASTNAME) String lastname,
    @JsonProperty(ALIAS_PLAIN_PASSWORD) String plainPassword,
    @JsonProperty(ALIAS_PGP_KEY) String pgpKey,
    @JsonProperty(ALIAS_PHONE) @Pattern(regexp = PHONE_REGEXP, message = PHONE_FORMAT) String phone,
    @JsonProperty(ALIAS_PHONE2) @Pattern(regexp = PHONE_REGEXP, message = PHONE_FORMAT)
        String phone2,
    @JsonProperty(ALIAS_ORGANIZATION) String organizationId,
    @JsonProperty(ALIAS_TAGS) List<String> tagIds,
    @JsonProperty(ALIAS_ADMIN) boolean admin,
    @JsonProperty(ALIAS_TENANTS) List<String> tenantIds) {}
