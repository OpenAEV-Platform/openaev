package io.openbas.utils.fixtures;

import io.openbas.database.model.Group;
import io.openbas.database.model.User;
import io.openbas.rest.user.form.login.LoginUserInput;
import io.openbas.rest.user.form.login.ResetUserInput;
import java.util.List;
import java.util.UUID;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

public class UserFixture {

  public static final String RAW_PASSWORD = "myPwd24!@";
  public static final String ENCODED_PASSWORD = getEncodePwd("myPwd24!@");

  private static String getEncodePwd(String rawPws) {
    return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8().encode(rawPws);
  }

  public static final String EMAIL = "user2@filigran.io";

  public static LoginUserInput.LoginUserInputBuilder getDefault() {
    return LoginUserInput.builder();
  }

  public static LoginUserInput.LoginUserInputBuilder getDefaultWithPwd() {
    return LoginUserInput.builder().password(RAW_PASSWORD);
  }

  public static LoginUserInput getLoginUserInput() {
    return LoginUserInput.builder().login(EMAIL).password(RAW_PASSWORD).build();
  }

  public static User getUserWithDefaultEmail() {
    User user = getUser();
    user.setEmail("user_email-%s@unittests.invalid".formatted(UUID.randomUUID()));
    return user;
  }

  public static User getUser() {
    return getUser("Firstname", "Lastname", EMAIL);
  }

  public static User getUser(List<Group> groups) {
    return getUser("Firstname", "Lastname", EMAIL, groups);
  }

  public static User getUser(String firstName, String lastName, String email, List<Group> groups) {
    User user = getUser(firstName, lastName, email);
    user.setGroups(groups);
    return user;
  }

  public static User getUser(String firstName, String lastName, String email, boolean isAdmin) {
    User user = new User();
    user.setFirstname(firstName);
    user.setLastname(lastName);
    user.setEmail(email);
    user.setPassword(ENCODED_PASSWORD);
    user.setAdmin(isAdmin);
    return user;
  }

  public static User getUser(String firstName, String lastName, String email) {
    return getUser(firstName, lastName, email, false);
  }

  public static User getAdminUser(String firstName, String lastName, String email) {
    return getUser(firstName, lastName, email, true);
  }

  public static User getSavedUser() {
    User user = getUser();
    user.setId("saved-user-id");
    return user;
  }

  public static ResetUserInput getResetUserInput() {
    ResetUserInput resetUserInput = new ResetUserInput();
    resetUserInput.setLogin(EMAIL);

    return resetUserInput;
  }
}
