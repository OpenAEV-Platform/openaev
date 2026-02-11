package io.openaev.sso;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Group;
import io.openaev.database.model.User;
import io.openaev.opencti.connectors.Constants;
import io.openaev.service.UserMappingService;
import io.openaev.utils.fixtures.GroupFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.GroupComposer;
import io.openaev.utils.fixtures.composers.UserComposer;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;


@Transactional
public class UserMappingServiceTest extends IntegrationTest {

  @Autowired
  private GroupComposer groupComposer;
  @Autowired
  UserComposer userComposer;
  @Autowired private UserMappingService userMappingService;
  @Autowired protected EntityManager entityManager;

  @BeforeEach
  public void setup() {
    groupComposer.reset();
  }

  @Test
  @DisplayName(
      "When the specific group already exists and the autocreate is false, add it to the user")
  public void whenTheSpecificGroupAlreadyExistsAndTheAutocreateIsFalse_addItToTheUser(){

    // -- ARRANGE ---
    String object = "[{\"IDPRole\": \"observer\",\"OAEVGroup\": \"observer\",\"autoCreate\": \"false\"}]";
    Group specificGroup = GroupFixture.createGroupWithName("observer");
    specificGroup.setId(Constants.PROCESS_STIX_GROUP_ID);
    specificGroup.setDescription("a description");
    specificGroup.setRoles(new ArrayList<>());
    groupComposer.forGroup(specificGroup).persist();
    entityManager.flush();
    entityManager.clear();
    User user = UserFixture.getUser();
    userComposer.forUser(user).persist();
    entityManager.flush();
    entityManager.clear();
    List<String> roles = List.of("observer");

    // ---- ACT ----
    userMappingService.mapCurrentUserWithGroup(object, user, roles);

    //-- ASSERT --
    assertTrue(user.getGroups().contains(specificGroup));
  }

  @Test
  @DisplayName(
      "When the specific group does not exist and the autocreate is true, create it and add it to the user")
  public void whenTheSpecificGroupDoesNotExistAndTheAutocreateIsTrue_createItAndAddItToTheUser(){

    // -- ARRANGE ---
    String object = "[{\"IDPRole\": \"observer\",\"OAEVGroup\": \"admin\",\"autoCreate\": \"true\"}]";
    Group specificGroup = GroupFixture.createGroupWithName("observer");
    specificGroup.setId(Constants.PROCESS_STIX_GROUP_ID);
    specificGroup.setDescription("a description");
    specificGroup.setRoles(new ArrayList<>());
    groupComposer.forGroup(specificGroup).persist();
    entityManager.flush();
    entityManager.clear();
    User user = UserFixture.getUser();
    userComposer.forUser(user).persist();
    entityManager.flush();
    entityManager.clear();
    List<String> roles = List.of("observer");

    // ---- ACT ----
    userMappingService.mapCurrentUserWithGroup(object, user, roles);

    //-- ASSERT --
    Group userGroup = user.getGroups().get(0);
    assertTrue(userGroup.getName().equals(specificGroup.getName()));
  }

  @Test
  @DisplayName(
      "When the specific group does not exist and the autocreate is false, do nothing")
  public void whenTheSpecificGroupDoesNotExistAndTheAutocreateIsFalse_doNothing(){

    // -- ARRANGE ---
    String object = "[{\"IDPRole\": \"observer\",\"OAEVGroup\": \"admin\",\"autoCreate\": \"false\"}]";
    Group specificGroup = GroupFixture.createGroupWithName("observer");
    specificGroup.setId(Constants.PROCESS_STIX_GROUP_ID);
    specificGroup.setDescription("a description");
    specificGroup.setRoles(new ArrayList<>());
    groupComposer.forGroup(specificGroup).persist();
    entityManager.flush();
    entityManager.clear();
    User user = UserFixture.getUser();
    userComposer.forUser(user).persist();
    entityManager.flush();
    entityManager.clear();
    List<String> roles = List.of("observer");

    // ---- ACT ----
    userMappingService.mapCurrentUserWithGroup(object, user, roles);

    //-- ASSERT --
    assertThat(user.getGroups().size()).isEqualTo(0);
  }

  @Test
  @DisplayName(
      "When group from idp and roles from oaev do not match, do nothing")
  public void whenGroupFromIdpAndRolesFromOaevDoNotMatch_doNothing(){

    // -- ARRANGE ---
    String object = "[{\"IDPRole\": \"observer\",\"OAEVGroup\": \"admin\",\"autoCreate\": \"false\"}]";
    Group specificGroup = GroupFixture.createGroupWithName("admin");
    specificGroup.setId(Constants.PROCESS_STIX_GROUP_ID);
    specificGroup.setDescription("a description");
    specificGroup.setRoles(new ArrayList<>());
    groupComposer.forGroup(specificGroup).persist();
    entityManager.flush();
    entityManager.clear();
    User user = UserFixture.getUser();
    userComposer.forUser(user).persist();
    entityManager.flush();
    entityManager.clear();
    List<String> roles = List.of("admin");

    // ---- ACT ----
    userMappingService.mapCurrentUserWithGroup(object, user, roles);

    //-- ASSERT --
    assertThat(user.getGroups().size()).isEqualTo(0);
  }

}

