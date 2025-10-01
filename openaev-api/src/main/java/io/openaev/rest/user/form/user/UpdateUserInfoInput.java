package io.openaev.rest.user.form.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateUserInfoInput {

  @JsonProperty("user_pgp_key")
  private String pgpKey;

  @JsonProperty("user_phone")
  private String phone;

  @JsonProperty("user_phone2")
  private String phone2;
}
