package io.openaev.rest;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.UserFixture.EMAIL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.User;
import io.openaev.database.repository.*;
import io.openaev.rest.user.form.login.LoginUserInput;
import io.openaev.rest.user.form.login.ResetUserInput;
import io.openaev.rest.user.form.user.ChangePasswordInput;
import io.openaev.service.MailingService;
import io.openaev.utils.RandomUtils;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.UserComposer;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class UserApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;

  @Autowired private UserRepository userRepository;

  @MockitoBean private MailingService mailingService;
  @MockitoBean private RandomUtils randomUtils;

  @Autowired private UserComposer userComposer;

  @Nested
  @DisplayName("Logging in")
  @Transactional
  class LoggingIn {

    @BeforeEach
    public void setup() {
      // Create user using composer if not already present, or restore password
      var existingUser = userRepository.findByEmailIgnoreCase(EMAIL);
      if (existingUser.isEmpty()) {
        User user = UserFixture.getUser("Test", "User", EMAIL);
        user.setPassword(UserFixture.ENCODED_PASSWORD);
        userComposer.forUser(user).persist();
      } else {
        // Restore password in case a previous test changed it
        User user = existingUser.get();
        user.setPassword(UserFixture.ENCODED_PASSWORD);
        userRepository.saveAndFlush(user);
      }
    }

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
                    .content(asJsonString(loginUserInput))
                    .with(csrf()))
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
                    .content(asJsonString(loginUserInput))
                    .with(csrf()))
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
                    .content(asJsonString(loginUserInput))
                    .with(csrf()))
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
                    .content(asJsonString(loginUserInput))
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("user_email").value(EMAIL));
      }
    }
  }

  @Nested
  // Disable transactionality;
  // the following tests go through async code that can't share transaction scopes
  @Transactional(propagation = Propagation.NEVER)
  @DisplayName("Reset Password from I forget my pwd option")
  class ResetPassword {
    private static final String EMAIL = "reset_user@openaev.invalid";

    @BeforeEach
    public void before() {
      reset(randomUtils, mailingService);
      // reset tokens leak from test to test
      // this is not a big deal, but we must guarantee that the mock
      // sets an actual value and not "null" in the reset token map
      when(randomUtils.getRandomAlphanumeric(anyInt())).thenReturn("reset_token");
      userRepository.save(UserFixture.getUser("Reset", "Me", EMAIL));
    }

    @AfterEach
    public void after() {
      userRepository.deleteAll();
    }

    @DisplayName("With a known email")
    @Test
    void resetPassword() throws Exception {
      // -- PREPARE --
      ResetUserInput input = UserFixture.getResetUserInput(EMAIL);

      // -- EXECUTE --
      mvc.perform(
              post("/api/reset")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- ASSERT --
      ArgumentCaptor<List<User>> userCaptor = ArgumentCaptor.forClass(List.class);
      // not ideal, but the actual reset happens in a background thread!
      Awaitility.await()
          .atMost(1, TimeUnit.SECONDS)
          .until(
              () -> {
                try {
                  verify(mailingService).sendEmail(anyString(), anyString(), userCaptor.capture());
                  return true;
                } catch (Exception e) {
                  return false;
                }
              });
      assertEquals(EMAIL, userCaptor.getValue().get(0).getEmail());
    }

    @DisplayName("Asking reset twice invalidates previous token")
    @Test
    void askingResetTwiceInvalidatesPreviousToken() throws Exception {
      // -- PREPARE --
      String firstToken = "et la tête";
      String secondToken = "alouette";
      when(randomUtils.getRandomAlphanumeric(anyInt())).thenReturn(firstToken, secondToken);

      ResetUserInput input = UserFixture.getResetUserInput(EMAIL);
      ChangePasswordInput changePasswordInput = UserFixture.getChangePasswordInput("le password");

      // -- EXECUTE --
      mvc.perform(
              post("/api/reset")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()))
          .andExpect(status().isOk());

      // not ideal, but the actual reset happens in a background thread!
      Awaitility.await()
          .atMost(1, TimeUnit.SECONDS)
          .until(
              () -> {
                try {
                  ArgumentCaptor<List<User>> userCaptor = ArgumentCaptor.forClass(List.class);
                  verify(mailingService, times(1))
                      .sendEmail(anyString(), anyString(), userCaptor.capture());
                  return true;
                } catch (Exception e) {
                  return false;
                }
              });

      mvc.perform(
              post("/api/reset")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()))
          .andExpect(status().isOk());

      // not ideal, but the actual reset happens in a background thread!
      Awaitility.await()
          .atMost(1, TimeUnit.SECONDS)
          .until(
              () -> {
                try {
                  ArgumentCaptor<List<User>> userCaptor = ArgumentCaptor.forClass(List.class);
                  verify(mailingService, times(2))
                      .sendEmail(anyString(), anyString(), userCaptor.capture());
                  return true;
                } catch (Exception e) {
                  return false;
                }
              });

      // -- ASSERT --
      mvc.perform(
              post("/api/reset/" + firstToken)
                  .content(asJsonString(changePasswordInput))
                  .contentType(MediaType.APPLICATION_JSON)
                  .with(csrf()))
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

      ResetUserInput input = UserFixture.getResetUserInput(EMAIL);
      ChangePasswordInput changePasswordInput = UserFixture.getChangePasswordInput("le password");

      // -- EXECUTE --
      mvc.perform(
              post("/api/reset")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()))
          .andExpect(status().isOk());

      Awaitility.await()
          .atMost(10, TimeUnit.SECONDS)
          .until(
              () -> {
                try {
                  ArgumentCaptor<List<User>> userCaptor = ArgumentCaptor.forClass(List.class);
                  verify(mailingService).sendEmail(anyString(), anyString(), userCaptor.capture());
                  return true;
                } catch (Exception e) {
                  return false;
                }
              });

      mvc.perform(
              post("/api/reset/" + firstToken)
                  .content(asJsonString(changePasswordInput))
                  .contentType(MediaType.APPLICATION_JSON)
                  .with(csrf()))
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
                  .contentType(MediaType.APPLICATION_JSON)
                  .with(csrf()))
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
      ResetUserInput input = UserFixture.getResetUserInput(EMAIL);
      input.setLogin("unknown@filigran.io");

      // -- EXECUTE --
      mvc.perform(
              post("/api/reset")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- ASSERT --
      verify(mailingService, never()).sendEmail(anyString(), anyString(), any(List.class));
    }
  }
}
