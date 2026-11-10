package io.openaev.rest.atomic_testing.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Builder
public class UserSimple {

  @JsonProperty("user_id")
  @NotBlank
  private String id;

  @JsonProperty("user_name")
  private String name;

  @JsonProperty("user_email")
  private String email;

  public static UserSimple of(String id, String firstname, String lastname, String email) {
    if (id == null) {
      return null;
    }
    return UserSimple.builder()
        .id(id)
        .name(displayName(firstname, lastname, email))
        .email(email)
        .build();
  }

  private static String displayName(String firstname, String lastname, String email) {
    boolean hasFirstname = firstname != null && !firstname.isBlank();
    boolean hasLastname = lastname != null && !lastname.isBlank();
    return hasFirstname && hasLastname ? firstname + " " + lastname : email;
  }
}
