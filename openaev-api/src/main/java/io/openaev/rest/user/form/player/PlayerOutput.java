package io.openaev.rest.user.form.player;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class PlayerOutput {

  @JsonProperty("user_id")
  @NotBlank
  private String id;

  @JsonProperty("user_firstname")
  private String firstname;

  @JsonProperty("user_lastname")
  private String lastname;

  @JsonProperty("user_email")
  @NotBlank
  private String email;

  @JsonProperty("user_organization")
  private String organization;

  @JsonProperty("user_country")
  private String country;

  @JsonProperty("user_phone")
  private String phone;

  @JsonProperty("user_phone2")
  private String phone2;

  @JsonProperty("user_pgp_key")
  private String pgpKey;

  @JsonProperty("user_tags")
  private Set<String> tags;
}
