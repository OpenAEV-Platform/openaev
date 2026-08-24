package io.openaev.rest.user;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import io.openaev.database.specification.TokenSpecification;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.platform.PlatformGroupComposer;
import io.openaev.utils.fixtures.platform.PlatformGroupFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@DisplayName("Me API tests")
public class MeApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantIsolationHelper;
  @Autowired private PlatformGroupComposer platformGroupComposer;
  @Autowired private GroupRepository groupRepository;
  @Autowired private TokenRepository tokenRepository;

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
  @DisplayName("POST /api/me/token/refresh")
  @WithMockUser(isAdmin = true)
  class RenewToken {

    @Test
    @DisplayName("given_ownedActiveToken_should_softDeleteOldToken_and_createNewActiveToken")
    void given_ownedActiveToken_should_softDeleteOldToken_and_createNewActiveToken()
        throws Exception {
      // Arrange
      User user = testUserHolder.get();
      Token token = new Token();
      token.setUser(user);
      token.setValue("old-token-value-" + System.nanoTime());
      token.setCreated(java.time.Instant.now());
      Token persistedToken = tokenRepository.save(token);
      String oldTokenValue = persistedToken.getValue();

      // Act
      MvcResult result =
          mvc.perform(
                  post(MeApi.ME_URI + "/token/refresh")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"token_id\":\"" + persistedToken.getId() + "\"}")
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
              .andReturn();

      // Assert
      String response = result.getResponse().getContentAsString();
      String renewedTokenId = JsonPath.read(response, "$.token_id");

      Token oldToken = tokenRepository.findById(persistedToken.getId()).orElseThrow();
      Token newToken = tokenRepository.findById(renewedTokenId).orElseThrow();

      assertNotNull(oldToken.getDeletedAt());
      assertTrue(oldToken.getValue().startsWith("[RENEWED:"));
      assertNotEquals(oldTokenValue, oldToken.getValue());
      assertTrue(tokenRepository.findByValueAndDeletedAtIsNull(oldTokenValue).isEmpty());

      assertNotEquals(oldToken.getId(), newToken.getId());
      assertTrue(newToken.getDeletedAt() == null);
      assertTrue(newToken.getUser().getId().equals(user.getId()));
    }

    @Test
    @DisplayName("given_deletedToken_should_notRenewToken")
    void given_deletedToken_should_notRenewToken() throws Exception {
      // Arrange
      User user = testUserHolder.get();
      Token token = new Token();
      token.setUser(user);
      token.setValue("deleted-token-value-" + System.nanoTime());
      token.setCreated(java.time.Instant.now());
      token.setDeletedAt(java.time.Instant.now());
      Token deletedToken = tokenRepository.save(token);

      // Act & Assert
      mvc.perform(
              post(MeApi.ME_URI + "/token/refresh")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"token_id\":\"" + deletedToken.getId() + "\"}")
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isNotFound());
    }
  }

  @Nested
  @DisplayName("GET /api/me/tokens")
  @WithMockUser(isAdmin = true)
  class GetMyTokens {

    @Test
    @DisplayName("Should return list of tokens for current user")
    void given_authenticatedUser_should_returnTokenList() throws Exception {
      // Arrange
      User user = testUserHolder.get();
      Token activeToken = new Token();
      activeToken.setUser(user);
      activeToken.setValue("active-token-" + System.nanoTime());
      activeToken.setCreated(java.time.Instant.now());
      tokenRepository.save(activeToken);

      Token deletedToken = new Token();
      deletedToken.setUser(user);
      deletedToken.setValue("deleted-token-" + System.nanoTime());
      deletedToken.setCreated(java.time.Instant.now());
      deletedToken.setDeletedAt(java.time.Instant.now());
      tokenRepository.save(deletedToken);

      // Act & Assert
      mvc.perform(get(MeApi.ME_URI + "/tokens").accept(MediaType.APPLICATION_JSON).with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$").isArray())
          .andExpect(jsonPath("$[?(@.token_deleted_at != null)]").isEmpty());

      List<Token> activeTokens =
          tokenRepository.findAll(
              TokenSpecification.fromUser(user.getId()).and(TokenSpecification.active()));
      assertTrue(activeTokens.stream().noneMatch(t -> t.getDeletedAt() != null));
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
}
