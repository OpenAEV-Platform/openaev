package io.openaev.rest.user;

import static io.openaev.rest.user.MeApi.ME_URI;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Group;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.GroupRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.user.form.me.UpdateMeEmailInput;
import io.openaev.rest.user.form.me.UpdateMePasswordInput;
import io.openaev.rest.user.form.me.UpdateProfileInput;
import io.openaev.service.MailingService;
import io.openaev.service.UserService;
import io.openaev.utils.RandomUtils;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.fixtures.platform.PlatformGroupComposer;
import io.openaev.utils.fixtures.platform.PlatformGroupFixture;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@DisplayName("Me API tests")
public class MeApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantIsolationHelper;
  @Autowired private PlatformGroupComposer platformGroupComposer;
  @Autowired private GroupRepository groupRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private UserService userService;
  @Autowired private EntityManager entityManager;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private io.openaev.utils.mockUser.TestUserHolder testUserHolder;
  @Autowired private UserComposer userComposer;
  @MockitoBean private RandomUtils mockRandomUtils;
  @MockitoBean private MailingService mockMailingService;

  @BeforeEach
  void before() {
    reset(mockRandomUtils, mockMailingService);
  }

  @Nested
  @WithMockUser
  @DisplayName("PUT /api/me/profile")
  class PutMeProfile {
    private static final String URI = ME_URI + "/profile";

    @Test
    @DisplayName("Given email field in profile update, then do not update email")
    void given_emailInUpdateRequest_then_doNotUpdateEmail() throws Exception {

      User me = testUserHolder.get();
      String expectedEmail = me.getEmail();

      UpdateProfileInput input = new UpdateProfileInput();
      String expectedFirstname = "Georges";
      String expectedLastname = "Abitbol";
      String expectedLang = "ja";
      String expectedTheme = "dark";
      input.setFirstname(expectedFirstname);
      input.setLastname(expectedLastname);
      input.setLang(expectedLang);
      input.setTheme(expectedTheme);

      ObjectNode jsonInput = objectMapper.valueToTree(input);
      jsonInput.put("user_email", "bad@evil.invalid");

      mvc.perform(
              put(URI)
                  .contentType(MediaType.APPLICATION_JSON)
                  .with(csrf())
                  .content(jsonInput.toString()))
          .andExpect(status().isOk());
      entityManager.flush();
      entityManager.clear();

      Optional<User> refetched = userRepository.findById(me.getId());

      assertThat(refetched)
          .isNotEmpty()
          .get()
          .satisfies(user -> assertThat(user.getEmail()).isEqualTo(expectedEmail))
          .satisfies(user -> assertThat(user.getFirstname()).isEqualTo(expectedFirstname))
          .satisfies(user -> assertThat(user.getLastname()).isEqualTo(expectedLastname))
          .satisfies(user -> assertThat(user.getTheme()).isEqualTo(expectedTheme))
          .satisfies(user -> assertThat(user.getLang()).isEqualTo(expectedLang));
    }
  }

  @Nested
  @WithMockUser
  @DisplayName("PUT /api/me/password")
  class PutMePassword {
    private static final String URI = ME_URI + "/password";

    @Test
    @DisplayName("Given correct current password field in update, then accept update")
    void given_correctPasswordInUpdate_then_acceptUpdate() throws Exception {
      String currentPassword = "current_user_password";
      String newPassword = "new_user_password";

      User me = testUserHolder.get();
      me.setPassword(userService.encodeUserPassword(currentPassword));
      userComposer.forUser(me).persist();
      entityManager.flush();
      entityManager.clear();

      UpdateMePasswordInput input = new UpdateMePasswordInput();
      input.setCurrentPassword(currentPassword);
      input.setPassword(newPassword);

      mvc.perform(
              put(URI)
                  .contentType(MediaType.APPLICATION_JSON)
                  .with(csrf())
                  .content(objectMapper.writeValueAsString(input)))
          .andExpect(status().isOk());
      entityManager.flush();
      entityManager.clear();

      Optional<User> refetched = userRepository.findById(me.getId());

      assertThat(refetched)
          .isNotEmpty()
          .get()
          .satisfies(
              user -> assertThat(userService.isUserPasswordValid(user, newPassword)).isTrue());
    }

    @Test
    @DisplayName("Given wrong current password field in update, then reject update")
    void given_wrongPasswordInUpdate_then_rejectUpdate() throws Exception {
      String currentPassword = "current_user_password";
      String wrongPassword = "WRONG";
      String newPassword = "new_user_password";

      User me = testUserHolder.get();
      me.setPassword(userService.encodeUserPassword(currentPassword));
      userComposer.forUser(me).persist();
      entityManager.flush();
      entityManager.clear();

      UpdateMePasswordInput input = new UpdateMePasswordInput();
      input.setCurrentPassword(wrongPassword);
      input.setPassword(newPassword);

      mvc.perform(
              put(URI)
                  .contentType(MediaType.APPLICATION_JSON)
                  .with(csrf())
                  .content(objectMapper.writeValueAsString(input)))
          .andExpect(status().isBadRequest());
      entityManager.flush();
      entityManager.clear();

      Optional<User> refetched = userRepository.findById(me.getId());

      assertThat(refetched)
          .isNotEmpty()
          .get()
          .satisfies(
              user -> assertThat(userService.isUserPasswordValid(user, newPassword)).isFalse());
    }

    @Test
    @DisplayName("Given no current password field in update, then reject update")
    void given_noPasswordInUpdate_then_rejectUpdate() throws Exception {
      String currentPassword = "current_user_password";
      String newPassword = "new_user_password";

      User me = testUserHolder.get();
      me.setPassword(userService.encodeUserPassword(currentPassword));
      userComposer.forUser(me).persist();
      entityManager.flush();
      entityManager.clear();

      // omit current password
      UpdateMePasswordInput input = new UpdateMePasswordInput();
      input.setPassword(newPassword);

      mvc.perform(
              put(URI)
                  .contentType(MediaType.APPLICATION_JSON)
                  .with(csrf())
                  .content(objectMapper.writeValueAsString(input)))
          .andExpect(status().isBadRequest());
      entityManager.flush();
      entityManager.clear();

      Optional<User> refetched = userRepository.findById(me.getId());

      assertThat(refetched)
          .isNotEmpty()
          .get()
          .satisfies(
              user -> assertThat(userService.isUserPasswordValid(user, newPassword)).isFalse());
    }
  }

  @Nested
  @WithMockUser
  @DisplayName("PUT /api/me/email")
  class PutMeEmail {
    private static final String URI = ME_URI + "/email";

    @Test
    @DisplayName(
        "Given correct current password field in update, then accept request but do not change email")
    void given_correctPasswordInUpdate_then_acceptUpdate() throws Exception {
      String currentPassword = "current_user_password";
      String newEmail = "new@good.invalid";

      User me = testUserHolder.get();
      String expectedEmail = me.getEmail();
      me.setPassword(userService.encodeUserPassword(currentPassword));
      userComposer.forUser(me).persist();
      entityManager.flush();
      entityManager.clear();

      UpdateMeEmailInput input = new UpdateMeEmailInput();
      input.setCurrentPassword(currentPassword);
      input.setEmail(newEmail);

      mvc.perform(
              put(URI)
                  .contentType(MediaType.APPLICATION_JSON)
                  .with(csrf())
                  .content(objectMapper.writeValueAsString(input)))
          .andExpect(status().isOk());
      entityManager.flush();
      entityManager.clear();

      Optional<User> refetched = userRepository.findById(me.getId());

      assertThat(refetched)
          .isNotEmpty()
          .get()
          .satisfies(user -> assertThat(user.getEmail()).isEqualTo(expectedEmail));
    }

    @Test
    @DisplayName("Given bad format email in update, then reject request")
    void given_badEmailFormat_then_rejectRequest() throws Exception {
      String currentPassword = "current_user_password";
      String newEmail = "not an email";

      User me = testUserHolder.get();
      String expectedEmail = me.getEmail();
      me.setPassword(userService.encodeUserPassword(currentPassword));
      userComposer.forUser(me).persist();
      entityManager.flush();
      entityManager.clear();

      UpdateMeEmailInput input = new UpdateMeEmailInput();
      input.setCurrentPassword(currentPassword);
      input.setEmail(newEmail);

      mvc.perform(
              put(URI)
                  .contentType(MediaType.APPLICATION_JSON)
                  .with(csrf())
                  .content(objectMapper.writeValueAsString(input)))
          .andExpect(status().isBadRequest());
      entityManager.flush();
      entityManager.clear();

      Optional<User> refetched = userRepository.findById(me.getId());

      assertThat(refetched)
          .isNotEmpty()
          .get()
          .satisfies(user -> assertThat(user.getEmail()).isEqualTo(expectedEmail));
    }

    @Test
    @DisplayName("Given already used email in update, then reject request")
    void given_alreadyUsedEmail_then_rejectRequest() throws Exception {
      String currentPassword = "current_user_password";

      UserComposer.Composer userWrapper =
          userComposer.forUser(UserFixture.getUser("Han", "Solo", "han@solo.invalid")).persist();

      String newEmail = userWrapper.get().getEmail();

      User me = testUserHolder.get();
      String expectedEmail = me.getEmail();
      me.setPassword(userService.encodeUserPassword(currentPassword));
      userComposer.forUser(me).persist();
      entityManager.flush();
      entityManager.clear();

      UpdateMeEmailInput input = new UpdateMeEmailInput();
      input.setCurrentPassword(currentPassword);
      input.setEmail(newEmail);

      mvc.perform(
              put(URI)
                  .contentType(MediaType.APPLICATION_JSON)
                  .with(csrf())
                  .content(objectMapper.writeValueAsString(input)))
          .andExpect(status().isBadRequest());
      entityManager.flush();
      entityManager.clear();

      Optional<User> refetched = userRepository.findById(me.getId());

      assertThat(refetched)
          .isNotEmpty()
          .get()
          .satisfies(user -> assertThat(user.getEmail()).isEqualTo(expectedEmail));
    }

    @Test
    @DisplayName("Given confirmation, then effect change")
    void given_confirmedUpdate_then_acceptUpdate() throws Exception {
      String currentPassword = "current_user_password";
      String newEmail = "new@good.invalid";
      String superSecretConfirmationCode = "JUST_DO_IT_GOOD";
      User me = testUserHolder.get();

      me.setPassword(userService.encodeUserPassword(currentPassword));
      userRepository.save(me);

      UpdateMeEmailInput input = new UpdateMeEmailInput();
      input.setCurrentPassword(currentPassword);
      input.setEmail(newEmail);

      when(mockRandomUtils.getRandomAlphanumeric(anyInt())).thenReturn(superSecretConfirmationCode);

      // put in request
      mvc.perform(
              put(URI)
                  .contentType(MediaType.APPLICATION_JSON)
                  .with(csrf())
                  .content(objectMapper.writeValueAsString(input)))
          .andExpect(status().isOk());

      mvc.perform(
              get(ME_URI + "/confirm-email-change/" + superSecretConfirmationCode)
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isFound());

      Optional<User> refetched = userRepository.findById(me.getId());

      assertThat(refetched)
          .isNotEmpty()
          .get()
          .satisfies(user -> assertThat(user.getEmail()).isEqualTo(newEmail));
    }

    @Test
    @DisplayName("Given wrong confirmation, then reject change")
    void given_wrongConfirmation_then_rejectUpdate() throws Exception {
      String currentPassword = "current_user_password";
      String newEmail = "new@good.invalid";
      String superSecretConfirmationCode = "JUST_DO_IT_BAD";
      String badConfirmationCode = "DONT_DO_IT";

      User me = testUserHolder.get();

      String expectedEmail = me.getEmail();
      me.setPassword(userService.encodeUserPassword(currentPassword));
      userRepository.save(me);

      UpdateMeEmailInput input = new UpdateMeEmailInput();
      input.setCurrentPassword(currentPassword);
      input.setEmail(newEmail);

      when(mockRandomUtils.getRandomAlphanumeric(anyInt())).thenReturn(superSecretConfirmationCode);

      // put in request
      mvc.perform(
              put(URI)
                  .contentType(MediaType.APPLICATION_JSON)
                  .with(csrf())
                  .content(objectMapper.writeValueAsString(input)))
          .andExpect(status().isOk());

      mvc.perform(
              get(ME_URI + "/confirm-email-change/" + badConfirmationCode)
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isNotFound());

      Optional<User> refetched = userRepository.findById(me.getId());

      assertThat(refetched)
          .isNotEmpty()
          .get()
          .satisfies(user -> assertThat(user.getEmail()).isEqualTo(expectedEmail));
    }

    @Test
    @DisplayName("Given wrong current password field in update, then reject update")
    void given_wrongPasswordInUpdate_then_rejectUpdate() throws Exception {
      String currentPassword = "current_user_password";
      String wrongPassword = "WRONG";
      String newEmail = "new@good.invalid";

      User me = testUserHolder.get();
      String expectedEmail = me.getEmail();
      me.setPassword(userService.encodeUserPassword(currentPassword));
      userComposer.forUser(me).persist();
      entityManager.flush();
      entityManager.clear();

      UpdateMeEmailInput input = new UpdateMeEmailInput();
      input.setCurrentPassword(wrongPassword);
      input.setEmail(newEmail);

      mvc.perform(
              put(URI)
                  .contentType(MediaType.APPLICATION_JSON)
                  .with(csrf())
                  .content(objectMapper.writeValueAsString(input)))
          .andExpect(status().isBadRequest());
      entityManager.flush();
      entityManager.clear();

      Optional<User> refetched = userRepository.findById(me.getId());

      assertThat(refetched)
          .isNotEmpty()
          .get()
          .satisfies(user -> assertThat(user.getEmail()).isEqualTo(expectedEmail));
    }

    @Test
    @DisplayName("Given no current password field in update, then reject update")
    void given_noPasswordInUpdate_then_rejectUpdate() throws Exception {
      String currentPassword = "current_user_password";
      String newEmail = "new@good.invalid";

      User me = testUserHolder.get();
      String expectedEmail = me.getEmail();
      me.setPassword(userService.encodeUserPassword(currentPassword));
      userComposer.forUser(me).persist();
      entityManager.flush();
      entityManager.clear();

      // omit the password
      UpdateMeEmailInput input = new UpdateMeEmailInput();
      input.setEmail(newEmail);

      mvc.perform(
              put(URI)
                  .contentType(MediaType.APPLICATION_JSON)
                  .with(csrf())
                  .content(objectMapper.writeValueAsString(input)))
          .andExpect(status().isBadRequest());
      entityManager.flush();
      entityManager.clear();

      Optional<User> refetched = userRepository.findById(me.getId());

      assertThat(refetched)
          .isNotEmpty()
          .get()
          .satisfies(user -> assertThat(user.getEmail()).isEqualTo(expectedEmail));
    }
  }

  @Nested
  @DisplayName("GET /api/me")
  @WithMockUser(isAdmin = true)
  class GetMe {

    @Test
    @DisplayName("Should return current user info")
    void given_authenticatedUser_should_returnUserInfo() throws Exception {
      // -------- Arrange --------
      // No specific setup needed — uses the mock user from @WithMockUser

      // -------- Act & Assert --------
      mvc.perform(get(ME_URI).accept(MediaType.APPLICATION_JSON).with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.user_id").isNotEmpty())
          .andExpect(jsonPath("$.user_email").isNotEmpty());
    }
  }

  @Nested
  @DisplayName("GET /api/me/tenants")
  @WithMockUser(isAdmin = true)
  class GetMyTenants {

    @Test
    @DisplayName("Should return list of tenants for current user")
    void given_authenticatedUser_should_returnTenantList() throws Exception {
      // -------- Arrange --------
      // No specific setup needed — uses the mock user from @WithMockUser

      // -------- Act & Assert --------
      mvc.perform(get(ME_URI + "/tenants").accept(MediaType.APPLICATION_JSON).with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$").isArray());
    }
  }

  @Nested
  @DisplayName("GET /api/me/tokens")
  @WithMockUser(isAdmin = true)
  class GetMyTokens {

    @Test
    @DisplayName("Should return list of tokens for current user")
    void given_authenticatedUser_should_returnTokenList() throws Exception {
      // -------- Arrange --------
      // No specific setup needed — uses the mock user from @WithMockUser

      // -------- Act & Assert --------
      mvc.perform(get(ME_URI + "/tokens").accept(MediaType.APPLICATION_JSON).with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$").isArray());
    }
  }

  @Nested
  @DisplayName("GET /api/logout")
  @WithMockUser(isAdmin = true)
  class Logout {

    @Test
    @DisplayName("Should return 200 OK")
    void given_authenticatedUser_should_logoutSuccessfully() throws Exception {
      // -------- Arrange --------
      // No specific setup needed — uses the mock user from @WithMockUser

      // -------- Act & Assert --------
      mvc.perform(get("/api/logout").accept(MediaType.APPLICATION_JSON).with(csrf()))
          .andExpect(status().isOk());
    }
  }

  @Nested
  @DisplayName("user_groups scoping — data leak prevention")
  @WithMockUser
  class UserGroupsScoping {

    @Test
    @DisplayName("Given platform context, user_groups should NOT leak tenant groups")
    void given_platformContext_should_notLeakTenantGroups() throws Exception {
      // -------- Arrange --------
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "ScopingLeak-Platform", Set.of(Capability.ACCESS_TENANT_SETTINGS));
      entityManager.flush();
      entityManager.clear();
      TenantContext.setCurrentTenant(null);

      // -------- Act — GET /api/me without tenantId in URL → TenantContext = DEFAULT (platform)
      // --------
      String response =
          mvc.perform(get(ME_URI).accept(MediaType.APPLICATION_JSON).with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -------- Assert --------
      List<String> userGroups = JsonPath.read(response, "$.user_groups");
      List<String> tenantXGroupIds =
          groupRepository.findAllByTenantId(tenantX.getId()).stream().map(Group::getId).toList();
      tenantXGroupIds.forEach(
          id ->
              assertFalse(
                  userGroups.contains(id),
                  "Tenant group [" + id + "] must NOT appear in platform context user_groups"));
    }

    @Test
    @DisplayName("Given tenant X context, user_groups should NOT leak groups from tenant Y")
    void given_tenantXContext_should_notLeakTenantYGroups() throws Exception {
      // -------- Arrange --------
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "ScopingLeak-TenantX", Set.of(Capability.ACCESS_TENANT_SETTINGS));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "ScopingLeak-TenantY", Set.of(Capability.ACCESS_TENANT_SETTINGS));
      entityManager.flush();
      entityManager.clear();

      // -------- Act — GET /api/tenants/{tenantX}/me → TenantContext = tenantX --------
      String response =
          mvc.perform(
                  get("/api/tenants/" + tenantX.getId() + "/me")
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -------- Assert --------
      List<String> userGroups = JsonPath.read(response, "$.user_groups");
      List<String> tenantYGroupIds =
          groupRepository.findAllByTenantId(tenantY.getId()).stream().map(Group::getId).toList();
      tenantYGroupIds.forEach(
          id ->
              assertFalse(
                  userGroups.contains(id),
                  "Tenant Y group [" + id + "] must NOT appear in tenant X context user_groups"));
    }

    @Test
    @DisplayName(
        "Given tenant context, user_groups should include both tenant group and platform group")
    void given_tenantContext_should_includeTenantAndPlatformGroups() throws Exception {
      // -------- Arrange --------
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "ScopingLeak-TenantXFull", Set.of(Capability.ACCESS_TENANT_SETTINGS));
      Group platformGroup =
          platformGroupComposer
              .forPlatformGroup(PlatformGroupFixture.getPlatformGroup("ScopingPlatformGroup"))
              .persist()
              .get();
      // Attach the platform group to the mock user via the unscoped collection
      User user = testUserHolder.get();
      user.getUnscopedGroups().add(platformGroup);
      entityManager.flush();
      entityManager.clear();

      // -------- Act --------
      String response =
          mvc.perform(
                  get("/api/tenants/" + tenantX.getId() + "/me")
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -------- Assert --------
      List<String> userGroups = JsonPath.read(response, "$.user_groups");
      assertTrue(
          userGroups.contains(platformGroup.getId()),
          "Platform group must appear in tenant context user_groups");
      List<String> tenantXGroupIds =
          groupRepository.findAllByTenantId(tenantX.getId()).stream().map(Group::getId).toList();
      assertFalse(tenantXGroupIds.isEmpty(), "Tenant X must have at least one group");
      tenantXGroupIds.forEach(
          id ->
              assertTrue(
                  userGroups.contains(id),
                  "Tenant X group [" + id + "] must appear in tenant X context user_groups"));
    }
  }
}
