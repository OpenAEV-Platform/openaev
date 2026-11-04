package io.openaev.migration;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Organization;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.OrganizationFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.OrganizationComposer;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * During a rolling deploy a pod running the previous version still writes the legacy {@code
 * users.user_organization} column. A trigger replays those writes on {@code users_organizations},
 * so they are not lost when the new version reads the join table.
 */
@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WithMockUser(isAdmin = true)
@DisplayName("users_organizations sync from the legacy user_organization column")
class UsersOrganizationsLegacySyncTest extends IntegrationTest {

  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private OrganizationComposer organizationComposer;
  @Autowired private UserComposer userComposer;
  @Autowired private EntityManager entityManager;

  private Tenant tenantA;
  private Tenant tenantB;
  private String prefix;

  @BeforeEach
  void setUp() throws Exception {
    organizationComposer.reset();
    userComposer.reset();
    tenantA = tenantHelper.createTenant("legacy-sync-a");
    tenantB = tenantHelper.createTenant("legacy-sync-b");
    prefix = "legacy-sync-" + UUID.randomUUID();
  }

  private Organization seedOrganization(Tenant tenant) {
    Organization organization = OrganizationFixture.createDefaultOrganisation();
    organization.setName(prefix + "-" + UUID.randomUUID());
    organization.setTenant(tenant);
    return organizationComposer.forOrganization(organization).persist().get();
  }

  private User seedUser() {
    User user = UserFixture.getUser("Legacy", "Sync", prefix + "@filigran.io");
    return userComposer.forUser(user).persist().get();
  }

  /** What an older pod does: it writes the single legacy column, nothing else. */
  private void legacyWrite(User user, Organization organization) {
    entityManager.flush();
    entityManager
        .createNativeQuery("UPDATE users SET user_organization = :org WHERE user_id = :id")
        .setParameter("org", organization == null ? null : organization.getId())
        .setParameter("id", user.getId())
        .executeUpdate();
  }

  private List<String> storedOrganizationIds(User user) {
    @SuppressWarnings("unchecked")
    List<String> ids =
        entityManager
            .createNativeQuery(
                "SELECT organization_id FROM users_organizations WHERE user_id = :id")
            .setParameter("id", user.getId())
            .getResultList();
    return ids;
  }

  @Test
  @DisplayName("An organization written by an older pod is linked to the user")
  void given_legacyWrite_should_linkTheOrganization() {
    // Arrange
    Organization organization = seedOrganization(tenantA);
    User user = seedUser();

    // Act
    legacyWrite(user, organization);

    // Assert
    assertThat(storedOrganizationIds(user)).containsExactly(organization.getId());
  }

  @Test
  @DisplayName("An older pod changing the organization replaces the one of the same tenant")
  void given_legacyChangeInSameTenant_should_replaceTheOrganization() {
    // Arrange
    Organization first = seedOrganization(tenantA);
    Organization second = seedOrganization(tenantA);
    User user = seedUser();
    legacyWrite(user, first);

    // Act
    legacyWrite(user, second);

    // Assert
    assertThat(storedOrganizationIds(user)).containsExactly(second.getId());
  }

  @Test
  @DisplayName(
      "An older pod linking an organization keeps the one a newer pod linked in another tenant")
  void given_legacyWrite_should_keepTheLinkOfAnotherTenant() {
    // Arrange
    Organization organizationA = seedOrganization(tenantA);
    Organization organizationB = seedOrganization(tenantB);
    User user = seedUser();
    entityManager
        .createNativeQuery(
            "INSERT INTO users_organizations (user_id, organization_id) VALUES (:id, :org)")
        .setParameter("id", user.getId())
        .setParameter("org", organizationB.getId())
        .executeUpdate();

    // Act
    legacyWrite(user, organizationA);

    // Assert
    assertThat(storedOrganizationIds(user))
        .containsExactlyInAnyOrder(organizationA.getId(), organizationB.getId());
  }

  @Test
  @DisplayName("An older pod moving the organization from a tenant to another keeps both links")
  void given_legacyChangeFromTenantAToTenantB_should_keepTheLinkOfTenantA() {
    // Arrange
    Organization organizationA = seedOrganization(tenantA);
    Organization organizationB = seedOrganization(tenantB);
    User user = seedUser();
    legacyWrite(user, organizationA);

    // Act
    legacyWrite(user, organizationB);

    // Assert
    assertThat(storedOrganizationIds(user))
        .containsExactlyInAnyOrder(organizationA.getId(), organizationB.getId());
  }

  @Test
  @DisplayName("An older pod clearing the organization removes the link it pointed to only")
  void given_legacyClear_should_removeOnlyTheLinkItPointedTo() {
    // Arrange
    Organization organizationA = seedOrganization(tenantA);
    Organization organizationB = seedOrganization(tenantB);
    User user = seedUser();
    legacyWrite(user, organizationA);
    entityManager
        .createNativeQuery(
            "INSERT INTO users_organizations (user_id, organization_id) VALUES (:id, :org)")
        .setParameter("id", user.getId())
        .setParameter("org", organizationB.getId())
        .executeUpdate();

    // Act
    legacyWrite(user, null);

    // Assert
    assertThat(storedOrganizationIds(user)).containsExactly(organizationB.getId());
  }
}
