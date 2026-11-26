package io.openaev.config;

import static io.openaev.config.SpringSessionConfig.SESSION_COOKIE_NAME;
import static io.openaev.config.TxCtxArgumentResolver.TENANT_IDS_HEADER;
import static io.openaev.rest.channel.ChannelApi.CHANNEL_URI;
import static io.openaev.rest.channel.ChannelApi.TENANT_CHANNEL_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.ProductionTablesIntegrationTest;
import io.openaev.config.cache.TenantMembershipCacheManager;
import io.openaev.config.security.SecurityService;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Group;
import io.openaev.database.model.Role;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.GroupRepository;
import io.openaev.database.repository.RoleRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.user.form.login.LoginUserInput;
import io.openaev.service.UserService;
import io.openaev.utils.AdminSessionTestHelper;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.helpers.SessionTestHelper;
import io.openaev.utilstest.WithoutTenantScope;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.saml2.provider.service.authentication.Saml2Authentication;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A request keeps the rights a tenant role gives its caller, with {@code roles} among the active
 * tables, whatever entry point authenticated the caller. A tenant role is a row of an active table:
 * a load of the caller made with no tenant scope reads none of them. The login, the single sign-on
 * login, the API token filter and the session restore all read the user with no scope; the rights
 * of the request come from the load the access check makes in the request's own transaction.
 *
 * <p>NOT {@code @Transactional}, and {@link WithoutTenantScope}: no test transaction is joined by
 * the request, so no scope and no user the test loaded reaches it. No mock user either: each caller
 * is authenticated the way production does it, the data is committed.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WithoutTenantScope
@DisplayName("Tenant role rights at the authentication entry points, with roles active")
class TenantRoleRightsAtEntryPointsTest extends ProductionTablesIntegrationTest {

  /** What the channel list checks ({@code CHANNEL} {@code SEARCH}). */
  private static final Capability GUARD_CAPABILITY = Capability.ACCESS_CHANNELS;

  /** A capability the channel list does not accept, for the refused caller. */
  private static final Capability OTHER_CAPABILITY = Capability.ACCESS_ASSESSMENT;

  private static final String SSO_REGISTRATION_ID = "t-roles-entry-points";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private UserService userService;
  @Autowired private AdminSessionTestHelper adminSession;
  @Autowired private SecurityService securityService;
  @Autowired private UserRepository userRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private GroupRepository groupRepository;
  @Autowired private TenantMembershipCacheManager membershipCache;
  @Autowired private SessionTestHelper sessionTestHelper;
  @Autowired private JdbcTemplate jdbc;

  private String tenantA;
  private User guardRoleInA;
  private User otherRoleInA;
  private User guardRoleInDefault;
  private User ssoGuardRoleInA;
  private String tokenOfGuardRoleInA;
  private String tokenOfGuardRoleInDefault;
  private final List<String> userIds = new ArrayList<>();
  private final List<String> groupIds = new ArrayList<>();
  private final List<String> roleIds = new ArrayList<>();

  @BeforeAll
  void seed() throws Exception {
    adminSession.open();
    try {
      tenantA = tenantHelper.createTenant("roles-entry-points-a").getId();
    } finally {
      SecurityContextHolder.clearContext();
      TenantContext.clearCurrentTenant();
    }
    guardRoleInA = newUser("guard-role-in-a");
    otherRoleInA = newUser("other-role-in-a");
    guardRoleInDefault = newUser("guard-role-in-default");
    ssoGuardRoleInA = newUser("sso-guard-role-in-a");
    tenantTx.executeAsPlatformWrite(
        TxCtx.forTenants(List.of(Tenant.DEFAULT_TENANT_UUID, tenantA)),
        () -> {
          guardRoleInA.getUnscopedGroups().add(tenantGroup(tenantA, GUARD_CAPABILITY));
          otherRoleInA.getUnscopedGroups().add(tenantGroup(tenantA, OTHER_CAPABILITY));
          guardRoleInDefault
              .getUnscopedGroups()
              .add(tenantGroup(Tenant.DEFAULT_TENANT_UUID, GUARD_CAPABILITY));
          ssoGuardRoleInA.getUnscopedGroups().add(tenantGroup(tenantA, GUARD_CAPABILITY));
          return userRepository.saveAll(
              List.of(guardRoleInA, otherRoleInA, guardRoleInDefault, ssoGuardRoleInA));
        });
    member(guardRoleInA, tenantA);
    member(otherRoleInA, tenantA);
    member(guardRoleInDefault, Tenant.DEFAULT_TENANT_UUID);
    member(ssoGuardRoleInA, tenantA);
    tokenOfGuardRoleInA = UUID.randomUUID().toString();
    userService.createUserToken(guardRoleInA, tokenOfGuardRoleInA);
    tokenOfGuardRoleInDefault = UUID.randomUUID().toString();
    userService.createUserToken(guardRoleInDefault, tokenOfGuardRoleInDefault);
    TenantContext.clearCurrentTenant();
  }

  @AfterAll
  void cleanup() {
    for (String userId : userIds) {
      jdbc.update("DELETE FROM tokens WHERE token_user = ?", userId);
      jdbc.update("DELETE FROM user_events WHERE user_id = ?", userId);
      jdbc.update("DELETE FROM users_groups WHERE user_id = ?", userId);
    }
    for (String groupId : groupIds) {
      jdbc.update("DELETE FROM groups_roles WHERE group_id = ?", groupId);
      jdbc.update("DELETE FROM groups WHERE group_id = ?", groupId);
    }
    for (String roleId : roleIds) {
      jdbc.update("DELETE FROM roles_capabilities WHERE role_id = ?", roleId);
      jdbc.update("DELETE FROM roles WHERE role_id = ?", roleId);
    }
    for (String userId : userIds) {
      jdbc.update("DELETE FROM users_tenants WHERE user_id = ?", userId);
      jdbc.update("DELETE FROM users WHERE user_id = ?", userId);
    }
    tenantHelper.deleteCommittedTenants(tenantA);
    TenantContext.clearCurrentTenant();
  }

  private User newUser(String label) {
    User user =
        UserFixture.getUser(
            label, label, label + "-" + UUID.randomUUID() + "@unittests.invalid", false);
    user.setPassword(UserFixture.ENCODED_PASSWORD);
    User saved = userRepository.save(user);
    userIds.add(saved.getId());
    return saved;
  }

  private Group tenantGroup(String tenantId, Capability capability) {
    Role role = new Role();
    role.setName("role-" + UUID.randomUUID());
    role.setCapabilities(Set.of(capability));
    role.setTenant(new Tenant(tenantId));
    Role savedRole = roleRepository.save(role);
    roleIds.add(savedRole.getId());
    Group group = new Group();
    group.setName("group-" + UUID.randomUUID());
    group.setTenant(new Tenant(tenantId));
    group.getRoles().add(savedRole);
    Group savedGroup = groupRepository.save(group);
    groupIds.add(savedGroup.getId());
    return savedGroup;
  }

  private void member(User user, String tenantId) {
    tenantTx.execute(
        TxCtx.forTenant(tenantId), () -> tenantRepository.addUserToTenant(user.getId(), tenantId));
    membershipCache.evict(user.getId(), tenantId);
  }

  /** The session cookie a password login answers with, as a browser keeps it. */
  private Cookie loginSession(User user) throws Exception {
    LoginUserInput input =
        LoginUserInput.builder().login(user.getEmail()).password(UserFixture.RAW_PASSWORD).build();
    Cookie session =
        mvc.perform(
                post("/api/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(input))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getCookie(SESSION_COOKIE_NAME);
    assertThat(session).isNotNull();
    return session;
  }

  /** The user a single sign-on login loads, through the service the OIDC and SAML flows call. */
  private User ssoLogin(User user) {
    User loaded =
        securityService.userManagement(
            user.getEmail(),
            SSO_REGISTRATION_ID,
            List.of(),
            List.of(),
            user.getFirstname(),
            user.getLastname());
    assertThat(loaded).isNotNull();
    return loaded;
  }

  private Cookie sessionOf(Authentication authentication, String userId) {
    return sessionTestHelper.cookieFor(
        sessionTestHelper.createAuthenticatedSession(authentication, userId));
  }

  @Nested
  @DisplayName("a session established by a password login and restored by the next request")
  class PasswordLoginSession {

    @Test
    @DisplayName("given_aTenantRoleOfTheRoute_should_passTheCapabilityCheck")
    void given_aTenantRoleOfTheRoute_should_passTheCapabilityCheck() throws Exception {
      // Arrange
      Cookie session = loginSession(guardRoleInA);

      // Act + Assert
      mvc.perform(get(TENANT_CHANNEL_URI, tenantA).cookie(session)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("given_aTenantRoleWithoutTheCapability_should_refuseTheCapabilityCheck")
    void given_aTenantRoleWithoutTheCapability_should_refuseTheCapabilityCheck() throws Exception {
      // Arrange
      Cookie session = loginSession(otherRoleInA);

      // Act + Assert
      mvc.perform(get(TENANT_CHANNEL_URI, tenantA).cookie(session))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("given_aDefaultTenantRoleOnThePlainRoute_should_passWithAndWithoutTheHeader")
    void given_aDefaultTenantRoleOnThePlainRoute_should_passWithAndWithoutTheHeader()
        throws Exception {
      // Arrange
      Cookie session = loginSession(guardRoleInDefault);

      // Act + Assert
      mvc.perform(get(CHANNEL_URI).cookie(session)).andExpect(status().isOk());
      mvc.perform(
              get(CHANNEL_URI)
                  .cookie(session)
                  .header(TENANT_IDS_HEADER, Tenant.DEFAULT_TENANT_UUID))
          .andExpect(status().isOk());
    }
  }

  @Nested
  @DisplayName("a session established by a single sign-on login")
  class SingleSignOnSession {

    @Test
    @DisplayName("given_anOidcLoginAndATenantRoleOfTheRoute_should_passTheCapabilityCheck")
    void given_anOidcLoginAndATenantRoleOfTheRoute_should_passTheCapabilityCheck()
        throws Exception {
      // Arrange
      OpenAEVOidcUser principal = new OpenAEVOidcUser(ssoLogin(ssoGuardRoleInA));
      Cookie session =
          sessionOf(
              new OAuth2AuthenticationToken(
                  principal, principal.getAuthorities(), SSO_REGISTRATION_ID),
              ssoGuardRoleInA.getId());

      // Act + Assert
      mvc.perform(get(TENANT_CHANNEL_URI, tenantA).cookie(session)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("given_aSamlLoginAndATenantRoleOfTheRoute_should_passTheCapabilityCheck")
    void given_aSamlLoginAndATenantRoleOfTheRoute_should_passTheCapabilityCheck() throws Exception {
      // Arrange
      List<SimpleGrantedAuthority> roles = List.of(new SimpleGrantedAuthority("ROLE_USER"));
      Cookie session =
          sessionOf(
              new Saml2Authentication(
                  new OpenAEVSaml2User(ssoLogin(ssoGuardRoleInA), roles), "saml-response", roles),
              ssoGuardRoleInA.getId());

      // Act + Assert
      mvc.perform(get(TENANT_CHANNEL_URI, tenantA).cookie(session)).andExpect(status().isOk());
    }
  }

  @Nested
  @DisplayName("an API token")
  class ApiToken {

    @Test
    @DisplayName("given_aTenantRoleOfTheRoute_should_passTheCapabilityCheck")
    void given_aTenantRoleOfTheRoute_should_passTheCapabilityCheck() throws Exception {
      // Act + Assert
      mvc.perform(
              get(TENANT_CHANNEL_URI, tenantA)
                  .header("Authorization", "Bearer " + tokenOfGuardRoleInA))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("given_aDefaultTenantRoleOnThePlainRoute_should_passWithAndWithoutTheHeader")
    void given_aDefaultTenantRoleOnThePlainRoute_should_passWithAndWithoutTheHeader()
        throws Exception {
      // Act + Assert
      mvc.perform(get(CHANNEL_URI).header("Authorization", "Bearer " + tokenOfGuardRoleInDefault))
          .andExpect(status().isOk());
      mvc.perform(
              get(CHANNEL_URI)
                  .header("Authorization", "Bearer " + tokenOfGuardRoleInDefault)
                  .header(TENANT_IDS_HEADER, Tenant.DEFAULT_TENANT_UUID))
          .andExpect(status().isOk());
    }
  }
}
