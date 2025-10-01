package io.openaev.rest.user.form.player;

import static io.openaev.config.AppConfig.EMAIL_FORMAT;
import static io.openaev.config.AppConfig.PHONE_FORMAT;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PlayerInput {

  @Email(message = EMAIL_FORMAT)
  @NotBlank
  @JsonProperty("user_email")
  private String email;

  @JsonProperty("user_firstname")
  private String firstname;

  @JsonProperty("user_lastname")
  private String lastname;

  @JsonProperty("user_organization")
  private String organizationId;

  @JsonProperty("user_country")
  private String country;

  @JsonProperty("user_tags")
  private List<String> tagIds = new ArrayList<>();

  @JsonProperty("user_teams")
  private List<String> teamIds = new ArrayList<>();

  @JsonProperty("user_phone")
  @Pattern(regexp = "^\\+[\\d\\s\\-.()]+$", message = PHONE_FORMAT)
  private String phone;

  @JsonProperty("user_phone2")
  @Pattern(regexp = "^\\+[\\d\\s\\-.()]+$", message = PHONE_FORMAT)
  private String phone2;

  @JsonProperty("user_pgp_key")
  private String pgpKey;
}
