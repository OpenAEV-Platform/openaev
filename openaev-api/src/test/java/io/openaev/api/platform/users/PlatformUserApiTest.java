package io.openaev.api.platform.users;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.UserFixture.getUser;
import static io.openaev.utils.fixtures.UserFixture.getUserInputWithTenants;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.api.users.dto.UserInput;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Group;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.GroupRepository;
import io.openaev.database.repository.TenantRepository;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.service.MailingService;
import io.openaev.service.UserService;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.fixtures.tenants.TenantComposer;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@DisplayName("Platform User API")
class PlatformUserApiTest extends IntegrationTest {

  private static final String PLATFORM_USERS_URI = "/api/platform-users";

  @Autowired private MockMvc mvc;
  @Autowired private TenantComposer tenantComposer;
  @Autowired private GroupRepository groupRepository;
  @Autowired private UserService userService;
  @Autowired private UserComposer userComposer;
  @Autowired private TenantRepository tenantRepository;

  @MockitoBean private EnterpriseEditionService enterpriseEditionService;
  @MockitoBean private MailingService mailingService;

  @BeforeEach
  void setup() {
    when(enterpriseEditionService.isEnterpriseLicenseInactive(any())).thenReturn(false);
  }

  @Test
  @WithMockUser(withCapabilities = {Capability.MANAGE_PLATFORM_USERS_GROUPS_AND_ROLES})
  @DisplayName("given_creationWithTenants_should_assignPlatformAndTenantAutoAssignGroups")
  @Transactional(propagation = Propagation.NEVER)
  void given_creationWithTenants_should_assignAutoAssignGroups() throws Exception {
    // -- ARRANGE --
    Tenant tenant =
        tenantComposer.forTenant(TenantFixture.getTenant("api-auto-assign")).persist().get();
    Group platformGroup = autoAssignGroup("api-platform-auto-assign", null);
    Group tenantGroup = autoAssignGroup("api-tenant-auto-assign", tenant);
    UserInput input =
        new UserInput(
            "api-auto-assign-" + UUID.randomUUID() + "@filigran.io",
            "Auto",
            "Assign",
            null,
            null,
            null,
            null,
            null,
            List.of(),
            false,
            List.of(tenant.getId()));

    // -- ACT --
    String response =
        mvc.perform(
                post(PLATFORM_USERS_URI)
                    .content(asJsonString(input))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    String userId = JsonPath.read(response, "$.user_id");
    User created = userService.user(userId);
    assertThat(created.getUnscopedGroups())
        .extracting(Group::getId)
        .contains(platformGroup.getId(), tenantGroup.getId());

    ArgumentCaptor<List<User>> userCaptor = ArgumentCaptor.forClass(List.class);
    Awaitility.await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                verify(mailingService, atLeastOnce())
                    .sendEmail(anyString(), anyString(), userCaptor.capture()));
    List<String> notifiedEmails = new ArrayList<>();
    for (List<User> users : userCaptor.getAllValues()) {
      notifiedEmails.addAll(users.stream().map(User::getEmail).toList());
    }
    assertThat(notifiedEmails).contains(input.email());
  }

  // The administrator of these cases is a member of the default tenant only
  // (autoJoinDefaultTenant), so attaching a user to another tenant writes outside the request's
  // scope: the write-attribution baseline lines of PlatformUserApi.create and .update apply to
  // exactly that write. An administrator with no membership has an empty scope, which the gate
  // does not check.
  @Nested
  @DisplayName("Membership of a tenant outside the administrator's own")
  class MembershipOutsideAdministratorTenants {

    @Test
    @WithMockUser(
        withCapabilities = {Capability.MANAGE_PLATFORM_USERS_GROUPS_AND_ROLES},
        autoJoinDefaultTenant = true)
    @DisplayName("given_creationAttachingAnotherTenant_should_attachTheUserToIt")
    @Transactional(propagation = Propagation.NEVER)
    void given_creationAttachingAnotherTenant_should_attachTheUserToIt() throws Exception {
      // -- ARRANGE --
      Tenant tenant =
          tenantComposer
              .forTenant(TenantFixture.getTenant("api-create-membership"))
              .persist()
              .get();
      UserInput input =
          getUserInputWithTenants(
              "api-create-membership-" + UUID.randomUUID() + "@filigran.io",
              "Create",
              "Membership",
              "secureP@ss1",
              List.of(tenant.getId()));

      // -- ACT --
      String response =
          mvc.perform(
                  post(PLATFORM_USERS_URI)
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isCreated())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- ASSERT --
      String userId = JsonPath.read(response, "$.user_id");
      assertThat(tenantRepository.existsByUserIdAndTenantId(userId, tenant.getId())).isTrue();
    }

    @Test
    @WithMockUser(
        withCapabilities = {Capability.MANAGE_PLATFORM_USERS_GROUPS_AND_ROLES},
        autoJoinDefaultTenant = true)
    @DisplayName("given_updateAttachingAnotherTenant_should_attachTheUserToIt")
    @Transactional(propagation = Propagation.NEVER)
    void given_updateAttachingAnotherTenant_should_attachTheUserToIt() throws Exception {
      // -- ARRANGE --
      Tenant tenant =
          tenantComposer
              .forTenant(TenantFixture.getTenant("api-update-membership"))
              .persist()
              .get();
      String email = "api-update-membership-" + UUID.randomUUID() + "@filigran.io";
      User user = userComposer.forUser(getUser("Update", "Membership", email)).persist().get();
      UserInput input =
          getUserInputWithTenants(
              email, "Update", "Membership", "secureP@ss1", List.of(tenant.getId()));

      // -- ACT --
      mvc.perform(
              put(PLATFORM_USERS_URI + "/" + user.getId())
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- ASSERT --
      assertThat(tenantRepository.existsByUserIdAndTenantId(user.getId(), tenant.getId())).isTrue();
    }
  }

  private Group autoAssignGroup(String name, Tenant tenant) {
    Group group = new Group();
    group.setName(name);
    group.setDefaultUserAssignation(true);
    group.setTenant(tenant);
    return groupRepository.save(group);
  }
}
