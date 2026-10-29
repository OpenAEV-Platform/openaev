package io.openaev.api.threat_arsenal;

import static io.openaev.api.threat_arsenal.ThreatArsenalApi.TENANT_THREAT_ARSENAL_URL;
import static io.openaev.api.threat_arsenal.ThreatArsenalApi.THREAT_ARSENAL_URL;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Filters;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Tenant;
import io.openaev.rest.injector_contract.input.InjectorContractSearchPaginationInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The threat arsenal bulk delete decides whether an item can be deleted in the tenant of the item,
 * the tenant it deletes the item from, on every route.
 *
 * <p>The items live in the default tenant. An item linked to an injector is a built-in action that
 * the bulk delete keeps; an item linked to no injector is removed. Both outcomes hold on the tenant
 * route and on the non-prefixed route, whatever tenants the request header names.
 *
 * <p>The production active tables are armed, so the class follows what production enforces. The
 * rows are committed, as a real request sees them, and removed after each test.
 */
@TestPropertySource(properties = "spring.quartz.auto-startup=false")
@TestInstance(PER_CLASS)
@DisplayName("Threat arsenal bulk delete evaluates each item in the item's own tenant")
class ThreatArsenalBulkDeleteEligibilityTenantTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private DataSource dataSource;

  private String otherTenantId;

  @AfterAll
  void deleteOtherTenant() {
    if (otherTenantId != null) {
      tenantHelper.deleteCommittedTenants(otherTenantId);
    }
  }

  private record CommittedItem(String contractId, String externalId, String injectorId) {}

  /** Commits a custom payload-less item in the default tenant, linked to an injector or not. */
  private CommittedItem commitItem(boolean linkedToInjector) {
    TenantContext.clearCurrentTenant();
    return new TransactionTemplate(transactionManager)
        .execute(
            status -> {
              InjectorContract contract = InjectorContractFixture.createDefaultInjectorContract();
              contract.setCustom(true);
              contract.setExternalId("bulk-delete-eligibility-" + UUID.randomUUID());
              if (!linkedToInjector) {
                contract.clearInjectors();
              }
              InjectorContract saved =
                  injectorContractComposer.forInjectorContract(contract).persist().get();
              return new CommittedItem(
                  saved.getId(),
                  saved.getExternalId(),
                  linkedToInjector ? saved.getInjectors().getFirst().getId() : null);
            });
  }

  private MockHttpServletRequestBuilder bulkDelete(String url, String contractId) {
    return post(url)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"injector_contract_ids_to_process\": [\"" + contractId + "\"]}");
  }

  /** Selects every item matching the item's external id, with no explicit ids. */
  private MockHttpServletRequestBuilder bulkDeleteAllMatching(String url, CommittedItem item) {
    Filters.Filter filter = new Filters.Filter();
    filter.setKey("injector_contract_external_id");
    filter.setValues(List.of(item.externalId()));
    filter.setOperator(Filters.FilterOperator.eq);
    Filters.FilterGroup filterGroup = new Filters.FilterGroup();
    filterGroup.setMode(Filters.FilterMode.and);
    filterGroup.setFilters(List.of(filter));
    InjectorContractSearchPaginationInput input = new InjectorContractSearchPaginationInput();
    input.setPage(0);
    input.setSize(10);
    input.setFilterGroup(filterGroup);
    return post(url).contentType(MediaType.APPLICATION_JSON).content(asJsonString(input));
  }

  /** Sends the request from the request thread's empty ambient tenant. */
  private int send(MockHttpServletRequestBuilder request) throws Exception {
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
    return mvc.perform(request.with(csrf())).andReturn().getResponse().getStatus();
  }

  // Read outside the tenant inspector: the assertions are about the stored rows, not about what a
  // scope lets the test see.
  private JdbcTemplate jdbc() {
    return new JdbcTemplate(dataSource);
  }

  private int storedContracts(CommittedItem item) {
    return jdbc()
        .queryForObject(
            "SELECT count(*) FROM injectors_contracts WHERE injector_contract_id = ?",
            Integer.class,
            item.contractId());
  }

  private void remove(CommittedItem item) {
    jdbc()
        .update(
            "DELETE FROM injectors_contracts WHERE injector_contract_id = ?", item.contractId());
    if (item.injectorId() != null) {
      jdbc().update("DELETE FROM injectors WHERE injector_id = ?", item.injectorId());
    }
  }

  private void joinOtherTenantOnly() {
    if (otherTenantId == null) {
      otherTenantId =
          new TransactionTemplate(transactionManager)
              .execute(
                  status -> {
                    try {
                      return tenantHelper.createTenant("bulk-delete-eligibility").getId();
                    } catch (Exception e) {
                      throw new IllegalStateException(e);
                    }
                  });
    }
    tenantHelper.attachCurrentUserToTenant(otherTenantId);
  }

  @Nested
  @DisplayName("An item linked to an injector")
  class LinkedItem {

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName("given a membership of its tenant should be kept")
    void given_membershipOfItsTenant_should_beKept() throws Exception {
      // Arrange
      CommittedItem item = commitItem(true);
      try {
        // Act
        send(bulkDelete(THREAT_ARSENAL_URL + "/bulk-delete", item.contractId()));

        // Assert
        assertThat(storedContracts(item)).as("the item is kept").isEqualTo(1);
      } finally {
        remove(item);
      }
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName("given the tenant route of its tenant should be kept")
    void given_tenantRouteOfItsTenant_should_beKept() throws Exception {
      // Arrange
      CommittedItem item = commitItem(true);
      try {
        // Act
        send(
            bulkDelete(
                TENANT_THREAT_ARSENAL_URL.replace("{tenantId}", Tenant.DEFAULT_TENANT_UUID)
                    + "/bulk-delete",
                item.contractId()));

        // Assert
        assertThat(storedContracts(item)).as("the item is kept").isEqualTo(1);
      } finally {
        remove(item);
      }
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName("given a header naming another tenant only should be kept")
    void given_headerNamingAnotherTenantOnly_should_beKept() throws Exception {
      // Arrange
      joinOtherTenantOnly();
      CommittedItem item = commitItem(true);
      try {
        // Act
        send(
            bulkDelete(THREAT_ARSENAL_URL + "/bulk-delete", item.contractId())
                .header("X-Tenant-Ids", otherTenantId));

        // Assert
        assertThat(storedContracts(item)).as("the item is kept").isEqualTo(1);
      } finally {
        remove(item);
      }
    }
  }

  @Nested
  @DisplayName("An item matched by a select-all request")
  class SelectedAll {

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName("given a header naming another tenant only should be kept when linked")
    void given_headerNamingAnotherTenantOnly_should_beKeptWhenLinked() throws Exception {
      // Arrange
      joinOtherTenantOnly();
      CommittedItem item = commitItem(true);
      try {
        // Act
        send(
            bulkDeleteAllMatching(THREAT_ARSENAL_URL + "/bulk-delete", item)
                .header("X-Tenant-Ids", otherTenantId));

        // Assert
        assertThat(storedContracts(item)).as("the item is kept").isEqualTo(1);
      } finally {
        remove(item);
      }
    }
  }

  @Nested
  @DisplayName("An item linked to no injector")
  class UnlinkedItem {

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName("given a header naming another tenant only should be removed")
    void given_headerNamingAnotherTenantOnly_should_beRemoved() throws Exception {
      // Arrange
      joinOtherTenantOnly();
      CommittedItem item = commitItem(false);
      try {
        // Act
        send(
            bulkDelete(THREAT_ARSENAL_URL + "/bulk-delete", item.contractId())
                .header("X-Tenant-Ids", otherTenantId));

        // Assert
        assertThat(storedContracts(item)).as("the item is removed").isZero();
      } finally {
        remove(item);
      }
    }
  }
}
