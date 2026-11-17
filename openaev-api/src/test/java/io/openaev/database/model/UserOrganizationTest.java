package io.openaev.database.model;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.context.TenantContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("User organization per tenant")
class UserOrganizationTest {

  private static final String TENANT_A = "tenant-a";
  private static final String TENANT_B = "tenant-b";

  private static Organization organization(String id, String tenantId) {
    Organization organization = new Organization();
    organization.setId(id);
    organization.setName(id);
    organization.setTenant(new Tenant(tenantId));
    return organization;
  }

  @Nested
  @DisplayName("Set the organization for a tenant")
  class SetOrganizationForTenant {

    @Test
    @DisplayName("Linking a user in tenant B keeps its organization of tenant A")
    void given_organizationInA_when_linkInB_should_keepBoth() {
      // Arrange
      User user = new User();
      Organization organizationA = organization("org-a", TENANT_A);
      Organization organizationB = organization("org-b", TENANT_B);
      user.setOrganizationForTenant(TENANT_A, organizationA);

      // Act
      user.setOrganizationForTenant(TENANT_B, organizationB);

      // Assert
      assertThat(user.organizationOfTenant(TENANT_A)).isEqualTo(organizationA);
      assertThat(user.organizationOfTenant(TENANT_B)).isEqualTo(organizationB);
    }

    @Test
    @DisplayName("Linking a user to another organization of the same tenant replaces the previous")
    void given_organizationInA_when_linkAnotherInA_should_replaceIt() {
      // Arrange
      User user = new User();
      Organization first = organization("org-a1", TENANT_A);
      Organization second = organization("org-a2", TENANT_A);
      user.setOrganizationForTenant(TENANT_A, first);

      // Act
      user.setOrganizationForTenant(TENANT_A, second);

      // Assert
      assertThat(user.organizationOfTenant(TENANT_A)).isEqualTo(second);
    }

    @Test
    @DisplayName("Clearing the organization in tenant B keeps the one of tenant A")
    void given_organizationsInAAndB_when_clearB_should_keepA() {
      // Arrange
      User user = new User();
      Organization organizationA = organization("org-a", TENANT_A);
      user.setOrganizationForTenant(TENANT_A, organizationA);
      user.setOrganizationForTenant(TENANT_B, organization("org-b", TENANT_B));

      // Act
      user.setOrganizationForTenant(TENANT_B, null);

      // Assert
      assertThat(user.organizationOfTenant(TENANT_A)).isEqualTo(organizationA);
      assertThat(user.organizationOfTenant(TENANT_B)).isNull();
    }
  }

  @Nested
  @DisplayName("Serialized organization")
  class GetOrganization {

    @Test
    @DisplayName("It is the organization of the current tenant, not the one of another tenant")
    void given_organizationsInAAndB_should_returnTheCurrentTenantOne() {
      // Arrange
      User user = new User();
      Organization organizationA = organization("org-a", TENANT_A);
      Organization organizationB = organization("org-b", TENANT_B);
      user.setOrganizationForTenant(TENANT_A, organizationA);
      user.setOrganizationForTenant(TENANT_B, organizationB);

      // Act
      TenantContext.setCurrentTenant(TENANT_B);
      Organization current;
      try {
        current = user.getOrganization();
      } finally {
        TenantContext.clearCurrentTenant();
      }

      // Assert
      assertThat(current).isEqualTo(organizationB);
    }

    @Test
    @DisplayName("It is null when the user has no organization in the current tenant")
    void given_organizationInAOnly_should_returnNullInB() {
      // Arrange
      User user = new User();
      user.setOrganizationForTenant(TENANT_A, organization("org-a", TENANT_A));

      // Act
      TenantContext.setCurrentTenant(TENANT_B);
      Organization current;
      try {
        current = user.getOrganization();
      } finally {
        TenantContext.clearCurrentTenant();
      }

      // Assert
      assertThat(current).isNull();
    }
  }
}
