package io.openaev.rest.tag;

import static io.openaev.rest.tag.TagApi.TAG_URI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Tenant;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pins that a capability-based create on the non-prefixed route never writes a row in a tenant
 * where the caller lacks the create capability, whether that tenant is the ambient one or the one
 * the {@code X-Tenant-Ids} header names. The caller is not an admin and holds the create capability
 * in one tenant and only read access in the other.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@WithMockUser
@DisplayName("Tag create on the non-prefixed route is authorized in every tenant it may write in")
class TagCreateHeaderRouteAuthorizationTest extends IntegrationTest {

  private static final String DEFAULT_TENANT = Tenant.DEFAULT_TENANT_UUID;
  private static final String TENANT_IDS_HEADER = "X-Tenant-Ids";
  private static final String NAME = "header-route-authorization-tag";

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  @AfterEach
  void clearAmbientTenant() {
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("Create capability in the default tenant, read only in tenant B")
  class CreateCapabilityInDefaultTenantOnly {

    @Test
    @DisplayName("A header naming B refuses the create")
    void given_headerNamingTenantWithoutCreateCapability_should_refuseCreate() throws Exception {
      // Arrange
      String tenantB = createInDefaultReadOnlyInNewTenant();

      // Act
      int status = perform(createTag().header(TENANT_IDS_HEADER, tenantB));

      // Assert
      assertRefusedWithoutRow(status);
    }

    @Test
    @DisplayName("No header writes in the default tenant")
    void given_noHeader_should_writeInDefaultTenant() throws Exception {
      // Arrange
      createInDefaultReadOnlyInNewTenant();

      // Act
      int status = perform(createTag());

      // Assert
      assertWrittenIn(status, DEFAULT_TENANT);
    }

    @Test
    @DisplayName("A header naming both tenants writes no row in B")
    void given_headerNamingSeveralTenants_should_notWriteInTenantWithoutCreateCapability()
        throws Exception {
      // Arrange
      String tenantB = createInDefaultReadOnlyInNewTenant();

      // Act
      int status = perform(createTag().header(TENANT_IDS_HEADER, DEFAULT_TENANT + "," + tenantB));

      // Assert
      assertAll(
          () -> assertEquals(HttpStatus.BAD_REQUEST.value(), status, "the create is refused"),
          () -> assertEquals(List.of(), rowTenants(), "a refused create writes no row"));
    }
  }

  @Nested
  @DisplayName("Create capability in tenant B, read only in the default tenant")
  class CreateCapabilityInOtherTenantOnly {

    @Test
    @DisplayName("A header naming B writes no row outside B")
    void given_headerNamingTenantWithCreateCapability_should_notWriteOutsideThatTenant()
        throws Exception {
      // Arrange
      String tenantB = createInNewTenantReadOnlyInDefault();

      // Act
      perform(createTag().header(TENANT_IDS_HEADER, tenantB));

      // Assert
      assertThat(rowTenants()).as("the row's tenant").isSubsetOf(tenantB);
    }

    @Test
    @DisplayName("A header naming the default tenant refuses the create")
    void given_headerNamingTenantWithoutCreateCapability_should_refuseCreate() throws Exception {
      // Arrange
      createInNewTenantReadOnlyInDefault();

      // Act
      int status = perform(createTag().header(TENANT_IDS_HEADER, DEFAULT_TENANT));

      // Assert
      assertRefusedWithoutRow(status);
    }
  }

  private String createInDefaultReadOnlyInNewTenant() throws Exception {
    tenantHelper.grantCapabilitiesInTenant(DEFAULT_TENANT, Set.of(Capability.MANAGE_TAGS));
    return tenantHelper
        .createTenantWithCapabilities("tag-capability-b", Set.of(Capability.ACCESS_TAGS))
        .getId();
  }

  private String createInNewTenantReadOnlyInDefault() throws Exception {
    tenantHelper.grantCapabilitiesInTenant(DEFAULT_TENANT, Set.of(Capability.ACCESS_TAGS));
    return tenantHelper
        .createTenantWithCapabilities("tag-capability-b", Set.of(Capability.MANAGE_TAGS))
        .getId();
  }

  private MockHttpServletRequestBuilder createTag() {
    // The non-prefixed route never sets the ambient tenant: reproduce production's empty
    // thread-local instead of inheriting what the fixtures left on the test thread.
    TenantContext.clearCurrentTenant();
    assertFalse(TenantContext.hasCurrentTenant(), "precondition: no ambient tenant");
    return post(TAG_URI)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"tag_name\":\"" + NAME + "\",\"tag_color\":\"#000000\"}")
        .with(csrf());
  }

  private int perform(MockHttpServletRequestBuilder request) throws Exception {
    int status = mvc.perform(request).andReturn().getResponse().getStatus();
    entityManager.flush();
    return status;
  }

  private void assertRefusedWithoutRow(int status) {
    assertAll(
        () -> assertEquals(HttpStatus.FORBIDDEN.value(), status, "the create is refused"),
        () -> assertEquals(List.of(), rowTenants(), "a refused create writes no row"));
  }

  private void assertWrittenIn(int status, String tenantId) {
    assertAll(
        () -> assertEquals(HttpStatus.OK.value(), status, "the create is accepted"),
        () -> assertEquals(List.of(tenantId), rowTenants(), "the row's tenant"));
  }

  private List<String> rowTenants() {
    return entityManager
        .createNativeQuery("SELECT tenant_id FROM tags WHERE tag_name = ?1", String.class)
        .setParameter(1, NAME)
        .getResultList();
  }
}
