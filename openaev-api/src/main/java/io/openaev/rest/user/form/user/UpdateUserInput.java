package io.openaev.rest.user.form.user;

import static io.openaev.config.AppConfig.EMAIL_FORMAT;
import static io.openaev.config.AppConfig.PHONE_FORMAT;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateUserInput {

  @Email(message = EMAIL_FORMAT)
  @JsonProperty("user_email")
  @Schema(description = "The email of the user")
  private String email;

  @JsonProperty("user_admin")
  @Schema(description = "True if the user is admin")
  private boolean admin;

  @JsonProperty("user_firstname")
  @Schema(description = "First name of the user")
  private String firstname;

  @JsonProperty("user_lastname")
  @Schema(description = "Last name of the user")
  private String lastname;

  @JsonProperty("user_organization")
  @Schema(description = "Organization of the user")
  private String organizationId;

  @JsonProperty("user_pgp_key")
  @Schema(description = "PGP key of the user")
  private String pgpKey;

  @JsonProperty("user_phone")
  @Schema(description = "Phone of the user")
  @Pattern(regexp = "^\\+[\\d\\s\\-.()]+$", message = PHONE_FORMAT)
  private String phone;

  @JsonProperty("user_phone2")
  @Schema(description = "Secondary phone of the user")
  @Pattern(regexp = "^\\+[\\d\\s\\-.()]+$", message = PHONE_FORMAT)
  private String phone2;

  @JsonProperty("user_tags")
  @Schema(description = "Tags of the user")
  private List<String> tagIds = new ArrayList<>();
}
