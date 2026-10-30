package io.openaev.api.threat_arsenal;

import static io.openaev.api.threat_arsenal.ThreatArsenalApi.TENANT_THREAT_ARSENAL_URL;
import static io.openaev.api.threat_arsenal.ThreatArsenalApi.THREAT_ARSENAL_URL;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The threat arsenal bulk delete only removes items whose tenant is in the request scope, and
 * decides whether each item can be deleted in that item's tenant, the tenant it deletes it from.
 * Explicit selections and select-all requests follow the same rule.
 *
 * <p>An item linked to an injector is a built-in action that the bulk delete keeps; an item linked
 * to no injector is removed. The caller is a member of the default tenant and of a second tenant,
 * and the request header narrows its scope on the non-prefixed route.
 *
 * <p>The production active tables are armed, so the class follows what production enforces. The
 * rows are committed, as a real request sees them, and removed after each test.
 */
@TestPropertySource(properties = "spring.quartz.auto-startup=false")
@TestInstance(PER_CLASS)
@DisplayName("Threat arsenal bulk delete acts within the request scope, in each item's own tenant")
class ThreatArsenalBulkDeleteEligibilityTenantTest extends IntegrationTest {

  private static final String BULK_DELETE_URI = "/bulk-delete";
  private static final String SEARCH_URI = "/search";
  private static final String TENANT_IDS_HEADER = "X-Tenant-Ids";

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

  private record CommittedItem(
      String tenantId, String contractId, String injectorId, String label) {}

  /**
   * Makes the caller a member of the default tenant (through {@code @WithMockUser}) and of a second
   * tenant, in every test, so the scope of a request without a header never depends on the order
   * the tests run in.
   */
  private void joinOtherTenant() {
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

  /** Commits a custom payload-less item in the given tenant, linked to an injector or not. */
  private CommittedItem commitItem(String tenantId, boolean linkedToInjector, String label) {
    TenantContext.setCurrentTenant(tenantId);
    try {
      return new TransactionTemplate(transactionManager)
          .execute(
              status -> {
                InjectorContract contract = InjectorContractFixture.createDefaultInjectorContract();
                contract.setCustom(true);
                contract.setExternalId("bulk-delete-eligibility-" + UUID.randomUUID());
                contract.setLabels(new HashMap<>(Map.of("en", label)));
                if (!linkedToInjector) {
                  contract.clearInjectors();
                }
                InjectorContract saved =
                    injectorContractComposer.forInjectorContract(contract).persist().get();
                return new CommittedItem(
                    tenantId,
                    saved.getId(),
                    linkedToInjector ? saved.getInjectors().getFirst().getId() : null,
                    label);
              });
    } finally {
      TenantContext.clearCurrentTenant();
    }
  }

  private static String newLabel() {
    return "bulk-delete-eligibility-" + UUID.randomUUID();
  }

  private static String routeOf(String tenantId) {
    return TENANT_THREAT_ARSENAL_URL.replace("{tenantId}", tenantId);
  }

  private MockHttpServletRequestBuilder bulkDelete(String url, CommittedItem... items) {
    List<String> ids = Arrays.stream(items).map(CommittedItem::contractId).toList();
    return post(url + BULK_DELETE_URI)
        .contentType(MediaType.APPLICATION_JSON)
        .content(asJsonString(Map.of("injector_contract_ids_to_process", ids)));
  }

  /** A search input selecting every item that carries the label, through a filterable field. */
  private static InjectorContractSearchPaginationInput labelledWith(String label) {
    Filters.Filter filter = new Filters.Filter();
    filter.setKey("action_labels");
    filter.setValues(List.of(label));
    filter.setOperator(Filters.FilterOperator.contains);
    Filters.FilterGroup filterGroup = new Filters.FilterGroup();
    filterGroup.setMode(Filters.FilterMode.and);
    filterGroup.setFilters(List.of(filter));
    InjectorContractSearchPaginationInput input = new InjectorContractSearchPaginationInput();
    input.setPage(0);
    input.setSize(10);
    input.setFilterGroup(filterGroup);
    return input;
  }

  /** Selects every item carrying the label, with no explicit ids. */
  private MockHttpServletRequestBuilder bulkDeleteAllLabelled(String url, String label) {
    return post(url + BULK_DELETE_URI)
        .contentType(MediaType.APPLICATION_JSON)
        .content(asJsonString(labelledWith(label)));
  }

  /** Sends the request from the request thread's empty ambient tenant. */
  private MockHttpServletResponse send(MockHttpServletRequestBuilder request) throws Exception {
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
    return mvc.perform(request.with(csrf())).andReturn().getResponse();
  }

  /** Sends a bulk delete, checks it succeeded and returns the ids it reports as deleted. */
  private List<String> deletedIds(MockHttpServletRequestBuilder request) throws Exception {
    MockHttpServletResponse response = send(request);
    assertThat(response.getStatus()).as("the bulk delete succeeds").isEqualTo(200);
    return JsonPath.read(response.getContentAsString(), "$.deleted_ids");
  }

  /**
   * Checks, through the search of the tenant route, that the select-all filter matches exactly the
   * given number of items in that tenant: the filter really filters, so a select-all request built
   * from it selects these items and nothing else.
   */
  private void assertLabelMatches(String tenantId, String label, int expected) throws Exception {
    MockHttpServletResponse response =
        send(
            post(routeOf(tenantId) + SEARCH_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(labelledWith(label))));
    assertThat(response.getStatus()).as("the search succeeds").isEqualTo(200);
    int total = JsonPath.read(response.getContentAsString(), "$.totalElements");
    assertThat(total).as("items matched by the select-all filter").isEqualTo(expected);
  }

  // Read outside the tenant inspector: the assertions are about the stored rows, not about what a
  // scope lets the test see.
  private JdbcTemplate jdbc() {
    return new JdbcTemplate(dataSource);
  }

  private int stored(CommittedItem item) {
    return jdbc()
        .queryForObject(
            "SELECT count(*) FROM injectors_contracts"
                + " WHERE injector_contract_id = ? AND tenant_id = ?",
            Integer.class,
            item.contractId(),
            item.tenantId());
  }

  private void remove(CommittedItem... items) {
    for (CommittedItem item : items) {
      if (item == null) {
        continue;
      }
      jdbc()
          .update(
              "DELETE FROM injectors_contracts WHERE injector_contract_id = ?", item.contractId());
      if (item.injectorId() != null) {
        jdbc().update("DELETE FROM injectors WHERE injector_id = ?", item.injectorId());
      }
    }
  }

  @Nested
  @DisplayName("A request whose scope excludes the item's tenant")
  class ScopeExcludingTheItemTenant {

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName("given an explicit selection should neither delete nor report the items")
    void given_explicitSelection_should_neitherDeleteNorReportTheItems() throws Exception {
      // Arrange
      joinOtherTenant();
      String label = newLabel();
      CommittedItem linked = null;
      CommittedItem unlinked = null;
      try {
        linked = commitItem(Tenant.DEFAULT_TENANT_UUID, true, label);
        unlinked = commitItem(Tenant.DEFAULT_TENANT_UUID, false, label);

        // Act
        List<String> deleted =
            deletedIds(
                bulkDelete(THREAT_ARSENAL_URL, linked, unlinked)
                    .header(TENANT_IDS_HEADER, otherTenantId));

        // Assert
        assertThat(deleted).as("no item is reported").isEmpty();
        assertThat(stored(linked)).as("the linked item is kept").isEqualTo(1);
        assertThat(stored(unlinked)).as("the unlinked item is kept").isEqualTo(1);
      } finally {
        remove(linked, unlinked);
      }
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName("given a select-all request should neither delete nor report the items")
    void given_selectAll_should_neitherDeleteNorReportTheItems() throws Exception {
      // Arrange
      joinOtherTenant();
      String label = newLabel();
      CommittedItem linked = null;
      CommittedItem unlinked = null;
      try {
        linked = commitItem(Tenant.DEFAULT_TENANT_UUID, true, label);
        unlinked = commitItem(Tenant.DEFAULT_TENANT_UUID, false, label);
        assertLabelMatches(Tenant.DEFAULT_TENANT_UUID, label, 2);

        // Act
        List<String> deleted =
            deletedIds(
                bulkDeleteAllLabelled(THREAT_ARSENAL_URL, label)
                    .header(TENANT_IDS_HEADER, otherTenantId));

        // Assert
        assertThat(deleted).as("no item is reported").isEmpty();
        assertThat(stored(linked)).as("the linked item is kept").isEqualTo(1);
        assertThat(stored(unlinked)).as("the unlinked item is kept").isEqualTo(1);
      } finally {
        remove(linked, unlinked);
      }
    }
  }

  @Nested
  @DisplayName("A request whose header names the item's tenant only")
  class HeaderNamingTheItemTenant {

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName("given an explicit selection should delete only the unlinked item")
    void given_explicitSelection_should_deleteOnlyTheUnlinkedItem() throws Exception {
      // Arrange
      joinOtherTenant();
      String label = newLabel();
      CommittedItem linked = null;
      CommittedItem unlinked = null;
      try {
        linked = commitItem(otherTenantId, true, label);
        unlinked = commitItem(otherTenantId, false, label);

        // Act
        List<String> deleted =
            deletedIds(
                bulkDelete(THREAT_ARSENAL_URL, linked, unlinked)
                    .header(TENANT_IDS_HEADER, otherTenantId));

        // Assert
        assertThat(deleted)
            .as("only the unlinked item is reported")
            .containsExactly(unlinked.contractId());
        assertThat(stored(linked)).as("the linked item is kept").isEqualTo(1);
        assertThat(stored(unlinked)).as("the unlinked item is removed").isZero();
      } finally {
        remove(linked, unlinked);
      }
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName("given a select-all request should delete only the unlinked item")
    void given_selectAll_should_deleteOnlyTheUnlinkedItem() throws Exception {
      // Arrange
      joinOtherTenant();
      String label = newLabel();
      CommittedItem linked = null;
      CommittedItem unlinked = null;
      try {
        linked = commitItem(otherTenantId, true, label);
        unlinked = commitItem(otherTenantId, false, label);
        assertLabelMatches(otherTenantId, label, 2);

        // Act
        List<String> deleted =
            deletedIds(
                bulkDeleteAllLabelled(THREAT_ARSENAL_URL, label)
                    .header(TENANT_IDS_HEADER, otherTenantId));

        // Assert
        assertThat(deleted)
            .as("only the unlinked item is reported")
            .containsExactly(unlinked.contractId());
        assertThat(stored(linked)).as("the linked item is kept").isEqualTo(1);
        assertThat(stored(unlinked)).as("the unlinked item is removed").isZero();
      } finally {
        remove(linked, unlinked);
      }
    }
  }

  @Nested
  @DisplayName("A request on the tenant route of the item's tenant")
  class TenantRouteOfTheItemTenant {

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName("given an explicit selection should delete only the unlinked item")
    void given_explicitSelection_should_deleteOnlyTheUnlinkedItem() throws Exception {
      // Arrange
      joinOtherTenant();
      String label = newLabel();
      CommittedItem linked = null;
      CommittedItem unlinked = null;
      try {
        linked = commitItem(otherTenantId, true, label);
        unlinked = commitItem(otherTenantId, false, label);

        // Act
        List<String> deleted = deletedIds(bulkDelete(routeOf(otherTenantId), linked, unlinked));

        // Assert
        assertThat(deleted)
            .as("only the unlinked item is reported")
            .containsExactly(unlinked.contractId());
        assertThat(stored(linked)).as("the linked item is kept").isEqualTo(1);
        assertThat(stored(unlinked)).as("the unlinked item is removed").isZero();
      } finally {
        remove(linked, unlinked);
      }
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName("given a select-all request should delete only the unlinked item")
    void given_selectAll_should_deleteOnlyTheUnlinkedItem() throws Exception {
      // Arrange
      joinOtherTenant();
      String label = newLabel();
      CommittedItem linked = null;
      CommittedItem unlinked = null;
      try {
        linked = commitItem(otherTenantId, true, label);
        unlinked = commitItem(otherTenantId, false, label);
        assertLabelMatches(otherTenantId, label, 2);

        // Act
        List<String> deleted = deletedIds(bulkDeleteAllLabelled(routeOf(otherTenantId), label));

        // Assert
        assertThat(deleted)
            .as("only the unlinked item is reported")
            .containsExactly(unlinked.contractId());
        assertThat(stored(linked)).as("the linked item is kept").isEqualTo(1);
        assertThat(stored(unlinked)).as("the unlinked item is removed").isZero();
      } finally {
        remove(linked, unlinked);
      }
    }
  }

  @Nested
  @DisplayName("A request without header from a member of the item's tenant")
  class MemberOfTheItemTenant {

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName("given an explicit selection should delete only the unlinked item")
    void given_explicitSelection_should_deleteOnlyTheUnlinkedItem() throws Exception {
      // Arrange
      joinOtherTenant();
      String label = newLabel();
      CommittedItem linked = null;
      CommittedItem unlinked = null;
      try {
        linked = commitItem(Tenant.DEFAULT_TENANT_UUID, true, label);
        unlinked = commitItem(Tenant.DEFAULT_TENANT_UUID, false, label);

        // Act
        List<String> deleted = deletedIds(bulkDelete(THREAT_ARSENAL_URL, linked, unlinked));

        // Assert
        assertThat(deleted)
            .as("only the unlinked item is reported")
            .containsExactly(unlinked.contractId());
        assertThat(stored(linked)).as("the linked item is kept").isEqualTo(1);
        assertThat(stored(unlinked)).as("the unlinked item is removed").isZero();
      } finally {
        remove(linked, unlinked);
      }
    }
  }
}
