package io.openaev.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.openaev.database.model.Organization;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.OrganizationRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DatabaseHelper tests")
class DatabaseHelperTest {

  private static final String TENANT_ID = "tenant-a";
  private static final String ORGANIZATION_ID = "organization-1";

  private final OrganizationRepository repository = mock(OrganizationRepository.class);

  @Nested
  @DisplayName("Resolving the organization of a tenant")
  class ResolveTenantOrganization {

    @Test
    @DisplayName("Given an empty id, should return null without querying")
    void given_emptyId_should_returnNull() {
      // Act
      Organization result = DatabaseHelper.resolveTenantOrganization("", TENANT_ID, repository);

      // Assert
      assertThat(result).isNull();
      verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("Given an organization of the tenant, should return it")
    void given_organizationOfTenant_should_returnIt() {
      // Arrange
      Organization organization = organizationOf(TENANT_ID);
      when(repository.findById(ORGANIZATION_ID)).thenReturn(Optional.of(organization));

      // Act
      Organization result =
          DatabaseHelper.resolveTenantOrganization(ORGANIZATION_ID, TENANT_ID, repository);

      // Assert
      assertThat(result).isSameAs(organization);
    }

    @Test
    @DisplayName("Given an unknown id, should fail instead of clearing the organization")
    void given_unknownId_should_throwNotFound() {
      // Arrange
      when(repository.findById(ORGANIZATION_ID)).thenReturn(Optional.empty());

      // Act & Assert
      assertThatThrownBy(
              () ->
                  DatabaseHelper.resolveTenantOrganization(ORGANIZATION_ID, TENANT_ID, repository))
          .isInstanceOf(ElementNotFoundException.class);
    }

    @Test
    @DisplayName("Given an organization of another tenant, should fail")
    void given_organizationOfAnotherTenant_should_throwNotFound() {
      // Arrange
      when(repository.findById(ORGANIZATION_ID))
          .thenReturn(Optional.of(organizationOf("tenant-b")));

      // Act & Assert
      assertThatThrownBy(
              () ->
                  DatabaseHelper.resolveTenantOrganization(ORGANIZATION_ID, TENANT_ID, repository))
          .isInstanceOf(ElementNotFoundException.class);
    }
  }

  private static Organization organizationOf(String tenantId) {
    Organization organization = new Organization();
    organization.setId(ORGANIZATION_ID);
    organization.setTenant(new Tenant(tenantId));
    return organization;
  }
}
