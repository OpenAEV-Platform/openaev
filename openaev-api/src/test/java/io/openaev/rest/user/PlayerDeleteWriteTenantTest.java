package io.openaev.rest.user;

import static io.openaev.rest.user.PlayerApi.PLAYER_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.rest.user.form.player.PlayerBulkProcessingInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * A player is platform-level, so deleting one is addressed to a tenant: the endpoint removes the
 * player from the tenant the request writes in, as the tenant user endpoint does, and answers 404
 * for a player who is not a member of that tenant. The memberships are read from the database, on
 * the prefixed route and on the route that names the tenant in {@code X-Tenant-Ids}.
 */
@TestInstance(PER_CLASS)
@Transactional
@DisplayName("Player deletion in the tenant of the request")
public class PlayerDeleteWriteTenantTest extends IntegrationTest {

  /** Production's active tables: the handler reads and writes tables the inspector gates. */
  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  private static final String TENANT_HEADER = "X-Tenant-Ids";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantIsolationHelper;
  @Autowired private UserComposer userComposer;

  private String tenantA;
  private String tenantB;

  @BeforeEach
  void callerOfTheDefaultTenantAndB() throws Exception {
    tenantIsolationHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
    tenantB = tenantIsolationHelper.createTenantWithCurrentUser("player-delete-b").getId();
    tenantA = tenantIsolationHelper.createTenant("player-delete-a").getId();
  }

  @AfterEach
  void clearAmbientTenant() {
    TenantContext.clearCurrentTenant();
  }

  private User playerOf(String... tenantIds) {
    User player = userComposer.forUser(UserFixture.getUserWithDefaultEmail()).persist().get();
    for (String tenantId : tenantIds) {
      tenantIsolationHelper.attachUserToTenant(player.getId(), tenantId);
    }
    entityManager.flush();
    entityManager.clear();
    TenantContext.clearCurrentTenant();
    return player;
  }

  @SuppressWarnings("unchecked")
  private List<String> membershipsOf(User player) {
    entityManager.flush();
    return entityManager
        .createNativeQuery(
            "SELECT tenant_id FROM users_tenants WHERE user_id = :id ORDER BY tenant_id")
        .setParameter("id", player.getId())
        .getResultList();
  }

  private boolean exists(User player) {
    entityManager.flush();
    return ((Number)
                entityManager
                    .createNativeQuery("SELECT count(*) FROM users WHERE user_id = :id")
                    .setParameter("id", player.getId())
                    .getSingleResult())
            .intValue()
        == 1;
  }

  private MockHttpServletRequestBuilder deleteOne(User player, boolean prefixedRoute) {
    MockHttpServletRequestBuilder request =
        prefixedRoute
            ? delete("/api/tenants/" + tenantB + "/players/" + player.getId())
            : delete(PLAYER_URI + "/" + player.getId()).header(TENANT_HEADER, tenantB);
    return request.contentType(MediaType.APPLICATION_JSON).with(csrf());
  }

  private MockHttpServletRequestBuilder deleteMany(boolean prefixedRoute, User... players) {
    PlayerBulkProcessingInput input = new PlayerBulkProcessingInput();
    input.setUserIdsToProcess(List.of(players).stream().map(User::getId).toList());
    MockHttpServletRequestBuilder request =
        prefixedRoute
            ? delete("/api/tenants/" + tenantB + "/players")
            : delete(PLAYER_URI).header(TENANT_HEADER, tenantB);
    return request
        .content(asJsonString(input))
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .with(csrf());
  }

  @Nested
  @DisplayName("Delete of one player")
  class DeleteOne {

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given_playerOfAnotherTenantOnly_should_answerNotFoundOnThePrefixedRoute")
    void given_playerOfAnotherTenantOnly_should_answerNotFoundOnThePrefixedRoute()
        throws Exception {
      // Arrange
      User player = playerOf(tenantA);

      // Act
      mvc.perform(deleteOne(player, true))
          // Assert
          .andExpect(status().isNotFound());
      assertThat(exists(player)).isTrue();
      assertThat(membershipsOf(player)).containsExactly(tenantA);
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given_playerOfTheDefaultTenantOnly_should_answerNotFoundWhenTheHeaderNamesB")
    void given_playerOfTheDefaultTenantOnly_should_answerNotFoundWhenTheHeaderNamesB()
        throws Exception {
      // Arrange
      User player = playerOf(Tenant.DEFAULT_TENANT_UUID);

      // Act
      mvc.perform(deleteOne(player, false))
          // Assert
          .andExpect(status().isNotFound());
      assertThat(exists(player)).isTrue();
      assertThat(membershipsOf(player)).containsExactly(Tenant.DEFAULT_TENANT_UUID);
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given_memberOfBAndA_should_removeItFromBOnlyOnThePrefixedRoute")
    void given_memberOfBAndA_should_removeItFromBOnlyOnThePrefixedRoute() throws Exception {
      // Arrange
      User player = playerOf(tenantA, tenantB);

      // Act
      mvc.perform(deleteOne(player, true)).andExpect(status().is2xxSuccessful());

      // Assert
      assertThat(exists(player)).isTrue();
      assertThat(membershipsOf(player)).containsExactly(tenantA);
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given_memberOfBAndA_should_removeItFromBOnlyWhenTheHeaderNamesB")
    void given_memberOfBAndA_should_removeItFromBOnlyWhenTheHeaderNamesB() throws Exception {
      // Arrange
      User player = playerOf(tenantA, tenantB);

      // Act
      mvc.perform(deleteOne(player, false)).andExpect(status().is2xxSuccessful());

      // Assert
      assertThat(exists(player)).isTrue();
      assertThat(membershipsOf(player)).containsExactly(tenantA);
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given_memberOfBOnly_should_leaveThePlayerWithoutMembership")
    void given_memberOfBOnly_should_leaveThePlayerWithoutMembership() throws Exception {
      // Arrange
      User player = playerOf(tenantB);

      // Act
      mvc.perform(deleteOne(player, true)).andExpect(status().is2xxSuccessful());

      // Assert
      assertThat(exists(player)).isTrue();
      assertThat(membershipsOf(player)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Bulk delete of players")
  class DeleteMany {

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given_playersOfOtherTenants_should_leaveThemWhenTheHeaderNamesB")
    void given_playersOfOtherTenants_should_leaveThemWhenTheHeaderNamesB() throws Exception {
      // Arrange
      User ofDefault = playerOf(Tenant.DEFAULT_TENANT_UUID);
      User ofA = playerOf(tenantA);
      User ofBAndA = playerOf(tenantA, tenantB);

      // Act
      mvc.perform(deleteMany(false, ofDefault, ofA, ofBAndA)).andExpect(status().isOk());

      // Assert
      assertThat(exists(ofDefault)).isTrue();
      assertThat(membershipsOf(ofDefault)).containsExactly(Tenant.DEFAULT_TENANT_UUID);
      assertThat(exists(ofA)).isTrue();
      assertThat(membershipsOf(ofA)).containsExactly(tenantA);
      assertThat(exists(ofBAndA)).isTrue();
      assertThat(membershipsOf(ofBAndA)).containsExactly(tenantA);
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given_playersOfOtherTenants_should_leaveThemOnThePrefixedRoute")
    void given_playersOfOtherTenants_should_leaveThemOnThePrefixedRoute() throws Exception {
      // Arrange
      User ofA = playerOf(tenantA);
      User ofBAndA = playerOf(tenantA, tenantB);

      // Act
      mvc.perform(deleteMany(true, ofA, ofBAndA)).andExpect(status().isOk());

      // Assert
      assertThat(exists(ofA)).isTrue();
      assertThat(membershipsOf(ofA)).containsExactly(tenantA);
      assertThat(exists(ofBAndA)).isTrue();
      assertThat(membershipsOf(ofBAndA)).containsExactly(tenantA);
    }
  }
}
