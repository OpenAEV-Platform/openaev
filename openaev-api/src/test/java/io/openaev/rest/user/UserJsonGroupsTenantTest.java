package io.openaev.rest.user;

import static io.openaev.database.audit.ModelBaseListener.DATA_UPDATE;
import static io.openaev.rest.user.MeApi.TENANT_ME_URI;
import static io.openaev.rest.user.PlayerApi.TENANT_PLAYER_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.audit.BaseEvent;
import io.openaev.database.model.Group;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.GroupRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.helper.ObjectMapperHelper;
import io.openaev.rest.user.form.player.PlayerInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.PlayerFixture;
import io.openaev.utils.fixtures.TenantGroupFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.fixtures.platform.PlatformGroupComposer;
import io.openaev.utils.fixtures.platform.PlatformGroupFixture;
import io.openaev.utils.mockUser.TestUserHolder;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The JSON of a user carries the groups of the tenant the request works on and the platform groups,
 * under {@code user_groups}, and no other group collection is serialized.
 */
@TestInstance(PER_CLASS)
@Transactional
@DisplayName("User JSON groups of the request tenant")
public class UserJsonGroupsTenantTest extends IntegrationTest {

  private static final String UNSCOPED_GROUPS = "unscopedGroups";
  private static final String USER_GROUPS = "user_groups";

  /** Production's active tables: the handlers read tables the inspector gates. */
  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private TenantIsolationTestHelper tenantIsolationHelper;
  @Autowired private TestUserHolder testUserHolder;
  @Autowired private UserComposer userComposer;
  @Autowired private PlatformGroupComposer platformGroupComposer;
  @Autowired private GroupRepository groupRepository;
  @Autowired private UserRepository userRepository;

  private String tenantA;
  private Group groupOfDefault;
  private Group groupOfA;
  private Group platformGroup;

  /** Persists a group in the default tenant, one in tenant A and one on the platform. */
  private void aGroupInEachTenantAndOnThePlatform() throws Exception {
    tenantA = tenantIsolationHelper.createTenant("user-json-groups-a").getId();
    groupOfDefault = tenantGroup("Json default group", Tenant.DEFAULT_TENANT_UUID);
    groupOfA = tenantGroup("Json group of A", tenantA);
    platformGroup =
        platformGroupComposer
            .forPlatformGroup(PlatformGroupFixture.getPlatformGroup("Json platform group"))
            .persist()
            .get();
  }

  @AfterEach
  void clearAmbientTenant() {
    TenantContext.clearCurrentTenant();
  }

  private Group tenantGroup(String name, String tenantId) {
    Group group = TenantGroupFixture.getGroup(name);
    group.setTenant(new Tenant(tenantId));
    return groupRepository.save(group);
  }

  /** A user who belongs to the default tenant and to tenant A, holding the three groups. */
  private User memberOfBothTenantsWithEveryGroup(User user) {
    user.getUnscopedGroups().addAll(List.of(groupOfDefault, groupOfA, platformGroup));
    User saved = userRepository.save(user);
    entityManager.flush();
    tenantIsolationHelper.attachUserToTenant(saved.getId(), Tenant.DEFAULT_TENANT_UUID);
    tenantIsolationHelper.attachUserToTenant(saved.getId(), tenantA);
    return saved;
  }

  private Set<String> userGroupIds(JsonNode user) {
    return StreamSupport.stream(user.get(USER_GROUPS).spliterator(), false)
        .map(JsonNode::asText)
        .collect(Collectors.toSet());
  }

  private JsonNode bodyOf(org.springframework.test.web.servlet.ResultActions result)
      throws Exception {
    return objectMapper.readTree(
        result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
  }

  @Nested
  @DisplayName("GET /api/tenants/{tenantId}/me")
  class Me {

    @BeforeEach
    void groups() throws Exception {
      aGroupInEachTenantAndOnThePlatform();
    }

    @Test
    @WithMockUser
    @DisplayName("The groups of tenant A and the platform group are carried, and no other group")
    void given_userInTwoTenants_should_carryOnlyTheGroupsOfTheRequestTenantAndThePlatform()
        throws Exception {
      // Arrange
      memberOfBothTenantsWithEveryGroup(testUserHolder.get());
      entityManager.clear();

      // Act
      JsonNode body =
          bodyOf(mvc.perform(get(TENANT_ME_URI, tenantA).accept(MediaType.APPLICATION_JSON)));

      // Assert
      assertThat(body.has(UNSCOPED_GROUPS)).isFalse();
      assertThat(userGroupIds(body))
          .containsExactlyInAnyOrder(groupOfA.getId(), platformGroup.getId());
    }

    @Test
    @WithMockUser
    @DisplayName("The groups of the default tenant and the platform group are carried there")
    void given_userInTwoTenants_should_carryOnlyTheGroupsOfTheDefaultTenantAndThePlatform()
        throws Exception {
      // Arrange
      // The mock user already holds a group of the default tenant of its own.
      Set<String> heldInDefaultTenant =
          testUserHolder.get().getUnscopedGroups().stream()
              .map(Group::getId)
              .collect(Collectors.toSet());
      memberOfBothTenantsWithEveryGroup(testUserHolder.get());
      entityManager.clear();

      // Act
      JsonNode body =
          bodyOf(
              mvc.perform(
                  get(TENANT_ME_URI, Tenant.DEFAULT_TENANT_UUID)
                      .accept(MediaType.APPLICATION_JSON)));

      // Assert
      assertThat(body.has(UNSCOPED_GROUPS)).isFalse();
      Set<String> expected = new HashSet<>(heldInDefaultTenant);
      expected.addAll(Set.of(groupOfDefault.getId(), platformGroup.getId()));
      assertThat(userGroupIds(body)).isEqualTo(expected);
    }
  }

  @Nested
  @DisplayName("PUT /api/tenants/{tenantId}/players/{userId}")
  class PlayerUpdate {

    @BeforeEach
    void groups() throws Exception {
      aGroupInEachTenantAndOnThePlatform();
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("The updated player carries the groups of the request tenant and the platform")
    void given_playerInTwoTenants_should_carryOnlyTheGroupsOfTheRequestTenantAndThePlatform()
        throws Exception {
      // Arrange
      User player =
          memberOfBothTenantsWithEveryGroup(
              userComposer.forUser(UserFixture.getUserWithDefaultEmail()).persist().get());
      tenantIsolationHelper.attachCurrentUserToTenant(tenantA);
      entityManager.clear();
      PlayerInput input = PlayerFixture.createPlayerInput();
      input.setEmail(player.getEmail());
      input.setFirstname("Json groups");

      // Act
      JsonNode body =
          bodyOf(
              mvc.perform(
                  put(TENANT_PLAYER_URI + "/{userId}", tenantA, player.getId())
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf())));

      // Assert
      assertThat(body.has(UNSCOPED_GROUPS)).isFalse();
      assertThat(userGroupIds(body))
          .containsExactlyInAnyOrder(groupOfA.getId(), platformGroup.getId());
    }
  }

  @Nested
  @DisplayName("Serialization of a user outside a request")
  class Serialization {

    private User userHoldingEveryGroup() {
      User user = UserFixture.getUserWithDefaultEmail();
      user.setId(UUID.randomUUID().toString());
      user.getUnscopedGroups()
          .addAll(
              List.of(
                  TenantGroupFixture.getGroup("Json default group"),
                  TenantGroupFixture.getGroup("Json group of A"),
                  PlatformGroupFixture.getPlatformGroup("Json platform group")));
      return user;
    }

    @Test
    @DisplayName("The mapper of the API writes no unscopedGroups property")
    void given_userHoldingGroups_should_writeNoUnscopedGroupsProperty() {
      // Arrange
      User user = userHoldingEveryGroup();

      // Act
      JsonNode json = ObjectMapperHelper.openAEVJsonMapper().valueToTree(user);

      // Assert
      assertThat(json.has(UNSCOPED_GROUPS)).isFalse();
    }

    @Test
    @DisplayName("The event of a user update carries no unscopedGroups property")
    void given_userUpdateEvent_should_carryNoUnscopedGroupsProperty() {
      // Arrange
      User user = userHoldingEveryGroup();

      // Act
      BaseEvent event = new BaseEvent(DATA_UPDATE, user, ObjectMapperHelper.openAEVJsonMapper());

      // Assert
      assertThat(event.getInstanceData().has(UNSCOPED_GROUPS)).isFalse();
    }
  }
}
