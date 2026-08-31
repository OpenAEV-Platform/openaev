package io.openaev.api.platform.users;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.api.users.dto.UserInput;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Group;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.GroupRepository;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.service.UserService;
import io.openaev.utils.fixtures.tenants.TenantComposer;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@DisplayName("Platform User API")
class PlatformUserApiTest extends IntegrationTest {

  private static final String PLATFORM_USERS_URI = "/api/platform-users";

  @Autowired private MockMvc mvc;
  @Autowired private TenantComposer tenantComposer;
  @Autowired private GroupRepository groupRepository;
  @Autowired private UserService userService;
  @PersistenceContext private EntityManager entityManager;

  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  @BeforeEach
  void setup() {
    when(enterpriseEditionService.isEnterpriseLicenseInactive(any())).thenReturn(false);
  }

  @Test
  @WithMockUser(withCapabilities = {Capability.MANAGE_PLATFORM_USERS_GROUPS_AND_ROLES})
  @DisplayName("given_creationWithTenants_should_assignPlatformAndTenantAutoAssignGroups")
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
            "secureP@ss1",
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
    entityManager.flush();
    entityManager.clear();
    User created = userService.user(userId);
    assertThat(created.getUnscopedGroups())
        .extracting(Group::getId)
        .contains(platformGroup.getId(), tenantGroup.getId());
  }

  private Group autoAssignGroup(String name, Tenant tenant) {
    Group group = new Group();
    group.setName(name);
    group.setDefaultUserAssignation(true);
    group.setTenant(tenant);
    return groupRepository.save(group);
  }
}
