package io.openaev.injector_contract.variables.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.User;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class UserContract {
  @VariableContract(name = VARIABLE_FAMILY + ".id", description = "Id of the user in the platform")
  @JsonProperty("user_id")
  private final String id;

  @VariableContract(name = VARIABLE_FAMILY + ".email", description = "Email of the user")
  @JsonProperty("user_email")
  private final String email;

  @VariableContract(name = VARIABLE_FAMILY + ".firstname", description = "First name of the user")
  @JsonProperty("user_firstname")
  private final String firstname;

  @VariableContract(name = VARIABLE_FAMILY + ".lastname", description = "Last name of the user")
  @JsonProperty("user_lastname")
  private final String lastname;

  @VariableContract(name = VARIABLE_FAMILY + ".lang", description = "Language of the user")
  @JsonProperty("user_lang")
  private final String lang;

  @VariableContract(name = VARIABLE_FAMILY + ".pgpkey", description = "PGP key of the user")
  @JsonProperty("user_pgp_key")
  private final String pgpKey;

  @VariableContract(name = VARIABLE_FAMILY + ".phone", description = "Phone number of the user")
  @JsonProperty("user_phone")
  private final String phone;

  public static final String VARIABLE_FAMILY = "user";

  public static UserContract fromUser(User user) {
    return new UserContract(
        user.getId(),
        user.getEmail(),
        user.getFirstname(),
        user.getLastname(),
        user.getLang(),
        user.getPgpKey(),
        user.getPhone());
  }
}
