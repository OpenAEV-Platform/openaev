package io.openaev.rest.user.form.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class UserOutput {

  @JsonProperty("user_id")
  @NotBlank
  @Schema(description = "User ID")
  private String id;

  @JsonProperty("user_firstname")
  @Schema(description = "First name of the user")
  private String firstname;

  @JsonProperty("user_lastname")
  @Schema(description = "Last name of the user")
  private String lastname;

  @JsonProperty("user_email")
  @NotBlank
  @Schema(description = "Email of the user")
  private String email;

  @JsonProperty("user_admin")
  @Schema(description = "True if the user is admin")
  private boolean admin;

  @JsonProperty("user_organization_name")
  @Schema(description = "Organization of the user")
  private String organizationName;

  @JsonProperty("user_tags")
  @Schema(description = "Tags of the user")
  private Set<String> tags;
}
