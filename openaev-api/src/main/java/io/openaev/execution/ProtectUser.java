package io.openaev.execution;

import io.openaev.database.model.User;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ProtectUser {

  private String id;
  private String email;
  private String firstname;
  private String lastname;
  private String lang;
  private String pgpKey;
  private String phone;

  public ProtectUser(@NotNull final User user) {
    this.id = user.getId();
    this.email = user.getEmail();
    this.firstname = user.getFirstname();
    this.lastname = user.getLastname();
    this.lang = user.getLang();
    this.pgpKey = user.getPgpKey();
    this.phone = user.getPhone();
  }
}
