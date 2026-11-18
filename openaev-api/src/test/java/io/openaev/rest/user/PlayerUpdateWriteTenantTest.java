package io.openaev.rest.user;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Organization;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.OrganizationRepository;
import io.openaev.rest.user.form.player.PlayerInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.OrganizationFixture;
import io.openaev.utils.fixtures.PlayerFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
 * A player is platform-level, so the update of one is addressed to a tenant: the player must be a
 * member of the tenant the request writes in. A caller of tenant B who names a player that belongs
 * to tenant A only gets a 404 and nothing is written, while the same call for a member of B updates
 * the profile and links the organization of B. Ground truth is read from the database.
 */
@TestInstance(PER_CLASS)
@Transactional
@DisplayName("Player update in the tenant of the request")
public class PlayerUpdateWriteTenantTest extends IntegrationTest {

  /** Production's active tables: the handler reads and writes tables the inspector gates. */
  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantIsolationHelper;
  @Autowired private UserComposer userComposer;
  @Autowired private OrganizationRepository organizationRepository;

  private String tenantA;
  private String tenantB;
  private Organization organizationOfB;

  @BeforeEach
  void callerOfBWithAnOtherTenantA() throws Exception {
    tenantB = tenantIsolationHelper.createTenantWithCurrentUser("player-update-b").getId();
    tenantA = tenantIsolationHelper.createTenant("player-update-a").getId();
    Organization organization = OrganizationFixture.createDefaultOrganisation();
    organization.setTenant(new Tenant(tenantB));
    organizationOfB = organizationRepository.save(organization);
  }

  @AfterEach
  void clearAmbientTenant() {
    TenantContext.clearCurrentTenant();
  }

  private User memberOf(String tenantId) {
    User member = userComposer.forUser(UserFixture.getUserWithDefaultEmail()).persist().get();
    tenantIsolationHelper.attachUserToTenant(member.getId(), tenantId);
    entityManager.flush();
    entityManager.clear();
    return member;
  }

  /** The update the caller sends on the route of tenant B, renaming the player. */
  private MockHttpServletRequestBuilder updateInB(User player, String firstname) {
    PlayerInput input = PlayerFixture.createPlayerInput();
    input.setEmail(player.getEmail());
    input.setFirstname(firstname);
    input.setOrganizationId(organizationOfB.getId());
    return put("/api/tenants/" + tenantB + "/players/" + player.getId())
        .content(asJsonString(input))
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .with(csrf());
  }

  private String firstnameOf(User player) {
    entityManager.flush();
    return (String)
        entityManager
            .createNativeQuery("SELECT user_firstname FROM users WHERE user_id = :id")
            .setParameter("id", player.getId())
            .getSingleResult();
  }

  @SuppressWarnings("unchecked")
  private List<String> organizationsOf(User player) {
    entityManager.flush();
    return entityManager
        .createNativeQuery(
            "SELECT organization_id FROM users_organizations WHERE user_id = :id"
                + " ORDER BY organization_id")
        .setParameter("id", player.getId())
        .getResultList();
  }

  @Test
  @WithMockUser(isAdmin = true)
  @DisplayName("A player who is not a member of the tenant answers 404 and nothing is written")
  void given_playerOfAnotherTenant_should_answerNotFoundAndWriteNothing() throws Exception {
    // Arrange
    User player = memberOf(tenantA);
    String firstname = firstnameOf(player);

    // Act
    mvc.perform(updateInB(player, "Changed-" + UUID.randomUUID()))
        // Assert
        .andExpect(status().isNotFound());
    assertEquals(firstname, firstnameOf(player));
    assertEquals(List.of(), organizationsOf(player));
  }

  @Test
  @WithMockUser(isAdmin = true)
  @DisplayName("A member of the tenant is updated and gets the organization of that tenant")
  void given_playerOfTheTenant_should_updateItsProfileAndOrganization() throws Exception {
    // Arrange
    User player = memberOf(tenantB);
    String firstname = "Changed-" + UUID.randomUUID();

    // Act
    mvc.perform(updateInB(player, firstname)).andExpect(status().isOk());

    // Assert
    assertEquals(firstname, firstnameOf(player));
    assertEquals(List.of(organizationOfB.getId()), organizationsOf(player));
  }
}
