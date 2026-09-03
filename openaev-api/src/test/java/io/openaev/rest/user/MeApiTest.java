package io.openaev.rest.user;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Group;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.Token;
import io.openaev.database.model.User;
import io.openaev.database.repository.GroupRepository;
import io.openaev.database.repository.TokenRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.user.form.me.UpdateMePasswordInput;
import io.openaev.rest.user.form.me.UpdateProfileInput;
import io.openaev.service.UserService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.platform.PlatformGroupComposer;
import io.openaev.utils.fixtures.platform.PlatformGroupFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
  @Autowired private TokenRepository tokenRepository;
  @Autowired private UserService userService;

  @Value("${openbas.admin.token:${openaev.admin.token:#{null}}}")
  private String adminToken;

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
      mvc.perform(get(MeApi.ME_URI).accept(MediaType.APPLICATION_JSON).with(csrf()))
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
      mvc.perform(get(MeApi.ME_URI + "/tenants").accept(MediaType.APPLICATION_JSON).with(csrf()))
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
      mvc.perform(get(MeApi.ME_URI + "/tokens").accept(MediaType.APPLICATION_JSON).with(csrf()))
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
          mvc.perform(get(MeApi.ME_URI).accept(MediaType.APPLICATION_JSON).with(csrf()))
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

  @Nested
  @DisplayName("Self-service restrictions on the platform admin account")
  class PlatformAdminSelfService {

    // No @WithMockUser here on purpose: User#isExternal() is derived from the id
    // (id == ADMIN_UUID), so only the bootstrap admin trips these guards. Authenticating through
    // the configured admin bearer is the only way to reach them.
    // The class is @Transactional, so the admin row is restored on rollback.

    @Test
    @DisplayName("Given the admin, changing the email should be refused")
    void given_platformAdmin_should_refuseEmailChange() throws Exception {
      // -------- Arrange --------
      User admin = userRepository.findById(User.ADMIN_UUID).orElseThrow();
      String previousEmail = admin.getEmail();
      UpdateProfileInput input = profileInput("changed-" + UUID.randomUUID() + "@test.invalid");

      // -------- Act & Assert --------
      mvc.perform(
              put(MeApi.ME_URI + "/profile")
                  .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input)))
          .andExpect(status().isForbidden());

      entityManager.clear();
      assertThat(userRepository.findById(User.ADMIN_UUID).orElseThrow().getEmail())
          .isEqualTo(previousEmail);
    }

    @Test
    @DisplayName("Given the admin, updating the profile without touching the email is allowed")
    void given_platformAdmin_should_allowProfileUpdateWithoutEmailChange() throws Exception {
      // -------- Arrange --------
      // The guard must only trip on an actual email change, otherwise the admin could never edit
      // its language or theme either.
      User admin = userRepository.findById(User.ADMIN_UUID).orElseThrow();
      UpdateProfileInput input = profileInput(admin.getEmail());
      input.setFirstname("Renamed");

      // -------- Act & Assert --------
      mvc.perform(
              put(MeApi.ME_URI + "/profile")
                  .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.user_firstname").value("Renamed"));
    }

    @Test
    @DisplayName("Given the admin, changing the password should be refused")
    void given_platformAdmin_should_refusePasswordChange() throws Exception {
      // -------- Arrange --------
      UpdateMePasswordInput input = passwordInput();

      // -------- Act & Assert --------
      mvc.perform(
              put(MeApi.ME_URI + "/password")
                  .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input)))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Given the admin, renewing the token should be refused")
    void given_platformAdmin_should_refuseTokenRenewal() throws Exception {
      // -------- Arrange --------
      Token currentAdminToken = tokenRepository.findByValue(adminToken).orElseThrow();

      // -------- Act & Assert --------
      mvc.perform(
              post(MeApi.ME_URI + "/token/refresh")
                  .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(renewTokenBody(currentAdminToken.getId())))
          .andExpect(status().isForbidden());

      // The bearer must stay usable: a refused renewal never destroys the token.
      entityManager.clear();
      assertThat(tokenRepository.findByValue(adminToken)).isPresent();
    }
  }

  @Nested
  @DisplayName("Self-service updates for a regular user")
  @WithMockUser
  class RegularUserSelfService {

    @Test
    @DisplayName("Given a regular user, changing the email is allowed")
    void given_regularUser_should_allowEmailChange() throws Exception {
      // -------- Arrange --------
      String newEmail = "changed-" + UUID.randomUUID() + "@test.invalid";
      UpdateProfileInput input = profileInput(newEmail);

      // -------- Act & Assert --------
      mvc.perform(
              put(MeApi.ME_URI + "/profile")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.user_email").value(newEmail));
    }

    @Test
    @DisplayName("Given a regular user, changing the password is allowed")
    void given_regularUser_should_allowPasswordChange() throws Exception {
      // -------- Arrange --------
      User user = testUserHolder.get();
      user.setPassword(UserFixture.ENCODED_PASSWORD);
      entityManager.flush();
      UpdateMePasswordInput input = passwordInput();

      // -------- Act & Assert --------
      mvc.perform(
              put(MeApi.ME_URI + "/password")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Given a regular user, renewing the token is allowed")
    void given_regularUser_should_allowTokenRenewal() throws Exception {
      // -------- Arrange --------
      Token previousToken =
          userService.createUserToken(testUserHolder.get(), UUID.randomUUID().toString());
      entityManager.flush();

      // -------- Act --------
      String response =
          mvc.perform(
                  post(MeApi.ME_URI + "/token/refresh")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(renewTokenBody(previousToken.getId()))
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -------- Assert --------
      // A new token is issued: the regular user is allowed to renew. The hard-delete semantics of
      // the previous row are covered by UserServiceTest.
      String renewedTokenId = JsonPath.read(response, "$.token_id");
      assertThat(renewedTokenId).isNotBlank().isNotEqualTo(previousToken.getId());
    }
  }

  private UpdateProfileInput profileInput(String email) {
    UpdateProfileInput input = new UpdateProfileInput();
    input.setEmail(email);
    input.setFirstname("Self");
    input.setLastname("Service");
    input.setLang("auto");
    input.setTheme("auto");
    return input;
  }

  private UpdateMePasswordInput passwordInput() {
    UpdateMePasswordInput input = new UpdateMePasswordInput();
    input.setCurrentPassword(UserFixture.RAW_PASSWORD);
    input.setPassword("anotherPwd24!@");
    return input;
  }

  private String renewTokenBody(String tokenId) {
    return """
        {
          "token_id": "%s"
        }
        """
        .formatted(tokenId);
  }
}
