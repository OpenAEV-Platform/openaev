package io.openaev.api.groups;

import static io.openaev.api.groups.TenantGroupApi.GROUP_URI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Tenant;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code TenantGroupApi.createGroup} carries {@code @RequireTenantSelector}: with no selector
 * ({@code X-Tenant-Ids} or a {@code /{tenantId}} path) the write tenant is resolved from the
 * caller's memberships, same table as {@code TenantRoleApi} and the document create endpoints
 * (issues #6331 / #6332, extended to {@code groups} by this activation). A single-tenant caller
 * lands there, a multi-tenant caller with default access falls back to it, and a multi-tenant
 * caller without the default is refused with 400 rather than guessing.
 *
 * <p>The row's tenant is read by raw JDBC, on the Hibernate session's own connection: {@code
 * groups} is gated by the statement inspector, so a Hibernate-level read of the same row would be
 * rewritten by the statement inspector and is no ground truth.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName(
    "TenantGroupApi.createGroup resolves the write tenant from the caller with no selector")
class TenantGroupNoSelectorWriteScopeTest extends IntegrationTest {

  private static final String DEFAULT_TENANT = Tenant.DEFAULT_TENANT_UUID;

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  @Nested
  @DisplayName("POST /api/groups with no tenant selector")
  class NoSelector {

    @Test
    @DisplayName("given_singleTenantCaller_should_attributeRowToThatTenant")
    void given_singleTenantCaller_should_attributeRowToThatTenant() throws Exception {
      // Arrange: the caller belongs to exactly one tenant, so an absent selector is unambiguous.
      String tenant = tenantHelper.createTenantWithCurrentUser("group-nosel-single").getId();

      // Act
      String id = createWithoutSelector();

      // Assert
      assertThat(rowTenant(id))
          .as("a single-tenant caller with no selector must land the row in its own tenant")
          .isEqualTo(tenant);
    }

    @Test
    @DisplayName("given_multiTenantCallerWithDefault_should_fallBackToDefaultTenant")
    void given_multiTenantCallerWithDefault_should_fallBackToDefaultTenant() throws Exception {
      // Arrange: default tenant plus one more, so the platform-wide no-context convention
      // applies and the write falls back to the default tenant.
      tenantHelper.attachCurrentUserToTenant(DEFAULT_TENANT);
      tenantHelper.createTenantWithCurrentUser("group-nosel-multi-default");

      // Act
      String id = createWithoutSelector();

      // Assert
      assertThat(rowTenant(id))
          .as("a multi-tenant caller with default access must fall back to the default tenant")
          .isEqualTo(DEFAULT_TENANT);
    }

    @Test
    @DisplayName("given_multiTenantCallerWithoutDefault_should_return400")
    void given_multiTenantCallerWithoutDefault_should_return400() throws Exception {
      // Arrange: two non-default tenants and no default access, the one genuinely ambiguous case.
      tenantHelper.createTenantWithCurrentUser("group-nosel-multi-a");
      tenantHelper.createTenantWithCurrentUser("group-nosel-multi-b");

      // Act & Assert
      mvc.perform(createRequest().with(csrf())).andExpect(status().isBadRequest());
    }
  }

  private MockHttpServletRequestBuilder createRequest() {
    String body = "{\"group_name\":\"no-selector-" + UUID.randomUUID() + "\"}";
    return post(GROUP_URI).contentType(MediaType.APPLICATION_JSON).content(body);
  }

  private String createWithoutSelector() throws Exception {
    String response =
        mvc.perform(createRequest().with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(response, "$.group_id");
  }

  private String rowTenant(String groupId) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement("SELECT tenant_id FROM groups WHERE group_id = ?")) {
                statement.setString(1, groupId);
                try (ResultSet resultSet = statement.executeQuery()) {
                  return resultSet.next() ? resultSet.getString(1) : null;
                }
              }
            });
  }
}
