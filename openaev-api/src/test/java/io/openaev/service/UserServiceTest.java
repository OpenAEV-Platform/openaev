package io.openaev.service;

import static io.openaev.utils.fixtures.UserFixture.*;
import static org.assertj.core.api.Assertions.*;

import io.openaev.IntegrationTest;
import io.openaev.api.users.dto.UserInput;
import io.openaev.database.model.User;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.utils.fixtures.composers.UserComposer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserServiceTest extends IntegrationTest {

  @Autowired private UserService userService;
  @Autowired private UserComposer userComposer;

  // -- CREATE --

  @Test
  void given_validInput_should_createUser() {
    // -- ACT --
    UserInput input =
        getUserInputWithPasswordAndPhone(
            "create@test.invalid", "John", "Doe", "secureP@ss1", "+33612345678");
    User created = userService.createUser(input);

    // -- ASSERT --
    assertThat(created.getId()).isNotNull();
    assertThat(created.getEmail()).isEqualTo("create@test.invalid");
    assertThat(created.getFirstname()).isEqualTo("John");
    assertThat(created.getLastname()).isEqualTo("Doe");
    assertThat(created.getPassword()).isNotBlank();
    assertThat(created.getPhone()).isEqualTo("+33612345678");
  }

  // -- READ --

  @Test
  void given_existingUser_should_findUserById() {
    // -- ARRANGE --
    User persisted =
        userComposer.forUser(getUser("Read", "Test", "read@test.invalid")).persist().get();

    // -- ACT --
    User found = userService.user(persisted.getId());

    // -- ASSERT --
    assertThat(found.getEmail()).isEqualTo("read@test.invalid");
    assertThat(found.getFirstname()).isEqualTo("Read");
    assertThat(found.getLastname()).isEqualTo("Test");
  }

  // -- UPDATE --

  @Test
  void given_existingUser_should_updateUser() {
    // -- ARRANGE --
    User persisted =
        userComposer.forUser(getUser("Original", "Name", "update@test.invalid")).persist().get();

    // -- ACT --
    UserInput input =
        getUserInputWithPgpKey("updated@test.invalid", "Updated", "Lastname", "pgp-key-123");
    User updated = userService.updateUser(persisted.getId(), input);

    // -- ASSERT --
    assertThat(updated.getEmail()).isEqualTo("updated@test.invalid");
    assertThat(updated.getFirstname()).isEqualTo("Updated");
    assertThat(updated.getLastname()).isEqualTo("Lastname");
    assertThat(updated.getPgpKey()).isEqualTo("pgp-key-123");
  }

  // -- DELETE --

  @Test
  void given_existingUser_should_deleteUser() {
    // -- ARRANGE --
    User persisted =
        userComposer.forUser(getUser("Delete", "Me", "delete@test.invalid")).persist().get();

    // -- ACT --
    userService.delete(persisted.getId());

    // -- ASSERT --
    assertThatThrownBy(() -> userService.user(persisted.getId()))
        .isInstanceOf(ElementNotFoundException.class);
  }
}
