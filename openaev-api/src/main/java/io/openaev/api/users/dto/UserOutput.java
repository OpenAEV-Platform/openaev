package io.openaev.api.users.dto;

import static io.openaev.config.AppConfig.EMAIL_FORMAT;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Set;

public record UserOutput(
    @JsonProperty(ALIAS_ID) @NotBlank String id,
    @JsonProperty(ALIAS_EMAIL) @NotBlank @Email(message = EMAIL_FORMAT) String email,
    @JsonProperty(ALIAS_FIRSTNAME) String firstname,
    @JsonProperty(ALIAS_LASTNAME) String lastname,
    @JsonProperty(ALIAS_PGP_KEY) String pgpKey,
    @JsonProperty(ALIAS_PHONE) String phone,
    @JsonProperty(ALIAS_PHONE2) String phone2,
    @JsonProperty(ALIAS_ORGANIZATION_ID) String organizationId,
    @JsonProperty(ALIAS_ORGANIZATION_NAME) String organizationName,
    @JsonProperty(ALIAS_TAGS) Set<String> tags,
    @JsonProperty(ALIAS_ADMIN) boolean admin,
    @JsonProperty(ALIAS_TENANTS) List<UserTenantOutput> tenants) {

  public record UserTenantOutput(
      @JsonProperty("tenant_id") String id, @JsonProperty("tenant_name") String name) {}

  public static final String ALIAS_ID = "user_id";
  public static final String ALIAS_EMAIL = "user_email";
  public static final String ALIAS_FIRSTNAME = "user_firstname";
  public static final String ALIAS_LASTNAME = "user_lastname";
  public static final String ALIAS_ADMIN = "user_admin";
  public static final String ALIAS_PGP_KEY = "user_pgp_key";
  public static final String ALIAS_PHONE = "user_phone";
  public static final String ALIAS_PHONE2 = "user_phone2";
  public static final String ALIAS_ORGANIZATION_ID = "user_organization_id";
  public static final String ALIAS_ORGANIZATION_NAME = "user_organization_name";
  public static final String ALIAS_ORGANIZATION = "user_organization";
  public static final String ALIAS_PLAIN_PASSWORD = "user_plain_password";
  public static final String ALIAS_TAGS = "user_tags";
  public static final String ALIAS_TENANTS = "user_tenants";
}
