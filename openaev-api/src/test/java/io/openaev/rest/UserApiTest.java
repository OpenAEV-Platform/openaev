package io.openaev.rest;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.UserFixture.EMAIL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.api.users.dto.UserInput;
import io.openaev.database.model.Grant;
import io.openaev.database.model.Group;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.User;
import io.openaev.database.repository.*;
import io.openaev.rest.user.form.login.LoginUserInput;
import io.openaev.rest.user.form.login.ResetUserInput;
import io.openaev.rest.user.form.user.ChangePasswordInput;
import io.openaev.service.MailingService;
import io.openaev.utils.RandomUtils;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.OrganizationComposer;
import io.openaev.utils.fixtures.composers.TagComposer;
import io.openaev.utils.fixtures.composers.UserComposer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class UserApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;

  @Autowired private UserRepository userRepository;

  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private GroupRepository groupRepository;
  @Autowired private OrganizationRepository organizationRepository;
  @Autowired private GrantRepository grantRepository;
  @Autowired private TagComposer tagComposer;

  @MockitoBean private MailingService mailingService;
  @MockitoBean private RandomUtils randomUtils;

  @Autowired private UserComposer userComposer;
  @Autowired private OrganizationComposer organisationComposer;
  @Autowired private TagRepository tagRepository;

  @BeforeEach
  public void setup() {
    // Create user using composer if not already present
    if (this.userRepository.findByEmailIgnoreCase(EMAIL).isEmpty()) {
      User user = UserFixture.getUser("Test", "User", EMAIL);
      user.setPassword(UserFixture.ENCODED_PASSWORD);
      userComposer.forUser(user).persist();
    }
    entityManager.flush();
    entityManager.clear();
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

    @DisplayName("Asking reset twice invalidates previous token")
    @Test
    void askingResetTwiceInvalidatesPreviousToken() throws Exception {
      // -- PREPARE --
      String firstToken = "et la tête";
      String secondToken = "alouette";
      when(randomUtils.getRandomAlphanumeric(anyInt())).thenReturn(firstToken, secondToken);

      ResetUserInput input = UserFixture.getResetUserInput();
      ChangePasswordInput changePasswordInput = UserFixture.getChangePasswordInput("le password");

      // -- EXECUTE --
      mvc.perform(
              post("/api/reset")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input)))
          .andExpect(status().isOk());

      mvc.perform(
              post("/api/reset")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input)))
          .andExpect(status().isOk());

      // -- ASSERT --
      mvc.perform(
              post("/api/reset/" + firstToken)
                  .content(asJsonString(changePasswordInput))
                  .contentType(MediaType.APPLICATION_JSON))
          // should be 401 Access Denied
          // but some black magic is changing the actual status code
          // see RestBehavior.java
          // expecting vague 4xx code in case we fix this some day
          .andExpect(status().is4xxClientError());
    }

    @DisplayName("Consume token on successful reset")
    @Test
    void consumeTokenOnSuccessfulReset() throws Exception {
      // -- PREPARE --
      String firstToken = "et la tête";
      when(randomUtils.getRandomAlphanumeric(anyInt())).thenReturn(firstToken);

      ResetUserInput input = UserFixture.getResetUserInput();
      ChangePasswordInput changePasswordInput = UserFixture.getChangePasswordInput("le password");

      // -- EXECUTE --
      mvc.perform(
              post("/api/reset")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input)))
          .andExpect(status().isOk());

      mvc.perform(
              post("/api/reset/" + firstToken)
                  .content(asJsonString(changePasswordInput))
                  .contentType(MediaType.APPLICATION_JSON))
          // should be 401 Access Denied
          // but some black magic is changing the actual status code
          // see RestBehavior.java
          // expecting vague 4xx code in case we fix this some day
          .andExpect(status().isOk());

      // -- ASSERT --

      // cannot use same token again
      mvc.perform(
              post("/api/reset/" + firstToken)
                  .content(asJsonString(changePasswordInput))
                  .contentType(MediaType.APPLICATION_JSON))
          // should be 401 Access Denied
          // but some black magic is changing the actual status code
          // see RestBehavior.java
          // expecting vague 4xx code in case we fix this some day
          .andExpect(status().is4xxClientError());
    }

    @DisplayName("With a unknown email should return 200 OK")
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
          .andExpect(status().isOk());

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
    group.setGrants(new ArrayList<>(List.of(grantObserver, grantPlanner)));
    // Set user's groups (owning side) and save user to persist the relationship
    user.setGroups(new ArrayList<>(List.of(group)));
    userRepository.save(user);
    entityManager.flush();
    entityManager.clear();

    UserInput userInput =
        new UserInput(
            user.getEmail(),
            user.getFirstname(),
            user.getLastname(),
            null,
            null,
            null,
            null,
            null,
            null,
            false);

    mvc.perform(
            MockMvcRequestBuilders.put("/api/users/" + user.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(userInput)))
        .andExpect(status().is2xxSuccessful());

    // Assert — verify highest-grant logic on the reloaded entity
    User reloaded = userRepository.findById(user.getId()).orElseThrow();
    Map<String, String> grants = reloaded.getGrants();
    assertEquals(1, grants.size());
    assertEquals(Grant.GRANT_TYPE.PLANNER.name(), grants.get(scenario.getId()));
  }
}
