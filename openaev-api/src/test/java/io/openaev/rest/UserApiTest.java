package io.openaev.rest;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.UserFixture.EMAIL;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Grant;
import io.openaev.database.model.Group;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.User;
import io.openaev.database.repository.*;
import io.openaev.rest.user.form.login.LoginUserInput;
import io.openaev.rest.user.form.login.ResetUserInput;
import io.openaev.rest.user.form.user.CreateUserInput;
import io.openaev.rest.user.form.user.UpdateUserInput;
import io.openaev.service.MailingService;
import io.openaev.utils.fixtures.OrganizationFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.TagFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.OrganizationComposer;
import io.openaev.utils.fixtures.composers.TagComposer;
import io.openaev.utils.fixtures.composers.UserComposer;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.javacrumbs.jsonunit.core.Option;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@TestInstance(PER_CLASS)
class UserApiTest extends IntegrationTest {

  private User savedUser;

  @Autowired private MockMvc mvc;

  @Autowired private UserRepository userRepository;

  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private GroupRepository groupRepository;
  @Autowired private OrganizationRepository organizationRepository;
  @Autowired private GrantRepository grantRepository;
  @Autowired private TagComposer tagComposer;

  @MockBean private MailingService mailingService;
  @Autowired private UserComposer userComposer;
  @Autowired private OrganizationComposer organisationComposer;
  @Autowired private TagRepository tagRepository;

  @BeforeAll
  public void setup() {
    // Create user
    User user = new User();
    user.setEmail(EMAIL);
    user.setPassword(UserFixture.ENCODED_PASSWORD);
    if (this.userRepository.findByEmailIgnoreCase(EMAIL).isEmpty()) {
      savedUser = this.userRepository.save(user);
    } else {
      savedUser = this.userRepository.findByEmailIgnoreCase(EMAIL).get();
    }
  }

  @AfterAll
  public void teardown() {
    this.scenarioRepository.deleteAll();
    this.userRepository.deleteAll();
    this.groupRepository.deleteAll();
    this.grantRepository.deleteAll();
    this.organizationRepository.deleteAll();
    tagRepository.deleteAll(this.tagComposer.generatedItems);
  }

  @Nested
  @DisplayName("Logging in")
  class LoggingIn {
    @Nested
    @DisplayName("Logging in by email")
    class LoggingInByEmail {
      @DisplayName("Retrieve user by email in lowercase succeed")
      @Test
      @WithMockUser
      void given_known_login_user_input_should_return_user() throws Exception {
        LoginUserInput loginUserInput = UserFixture.getLoginUserInput();

        mvc.perform(
                post("/api/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(loginUserInput)))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("user_email").value(EMAIL));
      }

      @DisplayName("Retrieve user by email failed")
      @Test
      @WithMockUser
      void given_unknown_login_user_input_should_throw_AccessDeniedException() throws Exception {
        LoginUserInput loginUserInput =
            UserFixture.getDefault().login("unknown@filigran.io").password("dontcare").build();

        mvc.perform(
                post("/api/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(loginUserInput)))
            .andExpect(status().is4xxClientError());
      }

      @DisplayName("Retrieve user by email in uppercase succeed")
      @Test
      @WithMockUser
      void given_known_login_user_in_uppercase_input_should_return_user() throws Exception {
        LoginUserInput loginUserInput =
            UserFixture.getDefaultWithPwd().login("USER2@filigran.io").build();

        mvc.perform(
                post("/api/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(loginUserInput)))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("user_email").value(EMAIL));
      }

      @DisplayName("Retrieve user by email in alternatingcase succeed")
      @Test
      @WithMockUser
      void given_known_login_user_in_alternatingcase_input_should_return_user() throws Exception {
        LoginUserInput loginUserInput =
            UserFixture.getDefaultWithPwd().login("uSeR2@filigran.io").build();

        mvc.perform(
                post("/api/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(loginUserInput)))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("user_email").value(EMAIL));
      }
    }
  }

  @Nested
  @DisplayName("Create user")
  class Creating {
    @DisplayName("Create existing user by email in lowercase gives a conflict")
    @Test
    @io.openaev.utils.mockUser.WithMockUser(isAdmin = true)
    void given_known_create_user_in_lowercase_input_should_return_conflict() throws Exception {
      CreateUserInput input = new CreateUserInput();
      input.setEmail(EMAIL);

      mvc.perform(
              post("/api/users")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input)))
          .andExpect(status().isConflict());
    }

    @DisplayName("Create existing user by email in uppercase gives a conflict")
    @Test
    @io.openaev.utils.mockUser.WithMockUser(isAdmin = true)
    void given_known_create_user_in_uppercase_input_should_return_conflict() throws Exception {
      CreateUserInput input = new CreateUserInput();
      input.setEmail(EMAIL.toUpperCase());

      mvc.perform(
              post("/api/users")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input)))
          .andExpect(status().isConflict());
    }
  }

  @Nested
  @DisplayName("Update the user")
  @io.openaev.utils.mockUser.WithMockUser(isAdmin = true)
  public class UpdateTheUser {
    @Test
    @DisplayName("Can update the user with an input object")
    public void canUpdateTheUserWithAnInputObject() throws Exception {
      UserComposer.Composer userWrapper =
          userComposer
              .forUser(UserFixture.getUser("Michel", "Angelo", "m.angelo@sixtine.invalid"))
              .persist();
      OrganizationComposer.Composer orgWrapper =
          organisationComposer
              .forOrganization(OrganizationFixture.createDefaultOrganisation())
              .persist();
      List<TagComposer.Composer> tagWrappers =
          List.of(
              tagComposer.forTag(TagFixture.getTagWithText("tag_1")).persist(),
              tagComposer.forTag(TagFixture.getTagWithText("tag_2")).persist(),
              tagComposer.forTag(TagFixture.getTagWithText("tag_3")).persist());

      UpdateUserInput updateUserInput = new UpdateUserInput();
      updateUserInput.setFirstname("New firstname");
      updateUserInput.setLastname("New lastname");
      updateUserInput.setEmail("new_email@domain.invalid");
      updateUserInput.setAdmin(!userWrapper.get().isAdmin());
      updateUserInput.setOrganizationId(orgWrapper.get().getId());
      updateUserInput.setPhone("+33123456789");
      updateUserInput.setPhone2("+33012345678");
      updateUserInput.setPgpKey("new pgp key");
      updateUserInput.setTagIds(tagWrappers.stream().map(tw -> tw.get().getId()).toList());

      String response =
          mvc.perform(
                  MockMvcRequestBuilders.put("/api/users/" + userWrapper.get().getId())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(updateUserInput)))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThatJson(response)
          .when(Option.IGNORING_ARRAY_ORDER)
          .whenIgnoringPaths(
              "listened", "user_id", "user_created_at", "user_updated_at", "user_gravatar")
          .isEqualTo(
              """
              {
                "listened":true,
                "user_id":"bb1d9737-0db0-467a-9daa-ce12deb8b247",
                "user_firstname":"New firstname",
                "user_lastname":"New lastname",
                "user_lang":"auto",
                "user_theme":"default",
                "user_email":"new_email@domain.invalid",
                "user_phone":"+33123456789",
                "user_phone2":"+33012345678",
                "user_pgp_key":"new pgp key",
                "user_status":0,
                "user_created_at":"2025-10-03T12:05:27.665735Z",
                "user_updated_at":"2025-10-03T12:05:27.665735Z",
                "user_organization":"%s",
                "user_admin":true,
                "user_country":null,
                "user_city":null,
                "user_groups":[],
                "user_teams":[],
                "user_tags":[%s],
                "user_communications":[],
                "team_exercises_users":[],
                "user_gravatar":"https://www.gravatar.com/avatar/48446ca219d9501c60a2fa161f24cc75?d=mm",
                "user_is_planner":true,
                "user_is_observer":true,
                "user_is_manager":true,
                "user_capabilities":[],
                "user_grants":{},
                "user_is_player":true,
                "user_is_external":false,
                "user_is_only_player":false,
                "user_is_admin_or_bypass":true
              }
              """
                  .formatted(
                      orgWrapper.get().getId(),
                      tagWrappers.stream()
                          .map(tw -> "\"%s\"".formatted(tw.get().getId()))
                          .collect(Collectors.joining(","))));
    }
  }

  @Nested
  @DisplayName("Reset Password from I forget my pwd option")
  class ResetPassword {
    @DisplayName("With a known email")
    @Test
    void resetPassword() throws Exception {
      // -- PREPARE --
      ResetUserInput input = UserFixture.getResetUserInput();

      // -- EXECUTE --
      mvc.perform(
              post("/api/reset")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input)))
          .andExpect(status().isOk());

      // -- ASSERT --
      ArgumentCaptor<List<User>> userCaptor = ArgumentCaptor.forClass(List.class);
      verify(mailingService).sendEmail(anyString(), anyString(), userCaptor.capture());
      assertEquals(EMAIL, userCaptor.getValue().get(0).getEmail());
    }

    @DisplayName("With a unknown email")
    @Test
    void resetPasswordWithUnknownEmail() throws Exception {
      // -- PREPARE --
      ResetUserInput input = UserFixture.getResetUserInput();
      input.setLogin("unknown@filigran.io");

      // -- EXECUTE --
      mvc.perform(
              post("/api/reset")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input)))
          .andExpect(status().isBadRequest());

      // -- ASSERT --
      verify(mailingService, never()).sendEmail(anyString(), anyString(), any(List.class));
    }
  }

  @DisplayName(
      "Get a user with several grant on the same resource, should return the highest grant")
  @Test
  @io.openaev.utils.mockUser.WithMockUser(isAdmin = true)
  void given_user_with_several_grant_on_same_resource_should_return_highest_grant()
      throws Exception {

    Scenario scenario = scenarioRepository.save(ScenarioFixture.createDefaultCrisisScenario());
    User user = userRepository.save(UserFixture.getUser("test", "test", "test3@gmail.com"));
    Group group = new Group();
    group.setName("test");
    group = groupRepository.save(group);

    Grant grantObserver = new Grant();
    grantObserver.setResourceId(scenario.getId());
    grantObserver.setGrantResourceType(Grant.GRANT_RESOURCE_TYPE.SCENARIO);
    grantObserver.setGroup(group);
    grantObserver.setName(Grant.GRANT_TYPE.OBSERVER);
    Grant grantPlanner = new Grant();
    grantPlanner.setResourceId(scenario.getId());
    grantPlanner.setGrantResourceType(Grant.GRANT_RESOURCE_TYPE.SCENARIO);
    grantPlanner.setGroup(group);
    grantPlanner.setName(Grant.GRANT_TYPE.PLANNER);
    grantRepository.saveAll(List.of(grantObserver, grantPlanner));
    group.setGrants(List.of(grantObserver, grantPlanner));
    group.setUsers(List.of(user));
    group = groupRepository.save(group);

    UpdateUserInput updateUserInput = new UpdateUserInput();
    updateUserInput.setFirstname(user.getFirstname());
    updateUserInput.setLastname(user.getLastname());
    updateUserInput.setEmail(user.getEmail());

    String response =
        mvc.perform(
                MockMvcRequestBuilders.put("/api/users/" + user.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(updateUserInput)))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    Map<String, Object> grants = JsonPath.read(response, "$.user_grants");
    assertEquals(1, grants.size(), 1);
    assertEquals(Grant.GRANT_TYPE.PLANNER.name(), grants.get(scenario.getId()));
  }
}
