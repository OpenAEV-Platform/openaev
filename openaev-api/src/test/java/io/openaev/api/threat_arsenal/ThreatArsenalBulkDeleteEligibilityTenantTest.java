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
import io.openaev.database.model.Capability;
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
import java.util.Set;
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
 * The threat arsenal bulk delete acts in exactly one tenant, the tenant the request writes to: the
 * tenant of the route, or the single tenant the header names. A scope that names several tenants,
 * or none, cannot designate it, so the request is refused with 400 and nothing is deleted. Explicit
 * selections and select-all requests follow the same rule.
 *
 * <p>The rule matters because an item id does not name a tenant: {@code injector_contract_id} is
 * part of a composite primary key with {@code tenant_id}, so two tenants onboarded from the same
 * source hold two rows under the same id. Acting in one tenant is what keeps a selection from
 * reaching the namesake rows of the others.
 *
 * <p>An item linked to an injector is a built-in action that the bulk delete keeps; an item linked
 * to no injector is removed. Eligibility is read, and the item deleted, in the write tenant. A
 * caller that does not hold the delete right there deletes nothing.
 *
 * <p>The production active tables are armed, so the class follows what production enforces. The
 * rows are committed, as a real request sees them, and removed after each test.
 */
@TestPropertySource(properties = "spring.quartz.auto-startup=false")
@TestInstance(PER_CLASS)
@DisplayName("Threat arsenal bulk delete acts in the request's write tenant")
class ThreatArsenalBulkDeleteEligibilityTenantTest extends IntegrationTest {

  private static final String BULK_DELETE_URI = "/bulk-delete";
  private static final String SEARCH_URI = "/search";
  private static final String TENANT_IDS_HEADER = "X-Tenant-Ids";
  private static final String WRITE_SCOPE_ERROR = "TENANT_WRITE_SCOPE";

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
   * tenant. Each test builds its own mock user, so a test that does not call this one keeps a
   * single-tenant membership whatever the order the tests run in.
   */
  private void joinOtherTenant() {
    createOtherTenant();
    tenantHelper.attachCurrentUserToTenant(otherTenantId);
  }

  /** The second tenant, created once for the class, without attaching the caller to it. */
  private void createOtherTenant() {
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
  }

  /** Commits a custom payload-less item in the given tenant, linked to an injector or not. */
  private CommittedItem commitItem(String tenantId, boolean linkedToInjector, String label) {
    return commit(tenantId, linkedToInjector, label, null);
  }

  /**
   * Commits an unlinked item under a chosen id, so that two tenants can hold the namesake rows a
   * datapack onboarded in both of them. Unlinked, because the fixture links its injector on the id
   * it generated itself.
   */
  private CommittedItem commitUnlinkedItem(String tenantId, String label, String contractId) {
    return commit(tenantId, false, label, contractId);
  }

  private CommittedItem commit(
      String tenantId, boolean linkedToInjector, String label, String contractId) {
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
                if (contractId != null) {
                  contract.setId(contractId);
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
    List<String> ids = Arrays.stream(items).map(CommittedItem::contractId).distinct().toList();
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

  /** Sends a bulk delete and checks the request scope could not designate a write tenant. */
  private void assertRefusedForWriteScope(MockHttpServletRequestBuilder request) throws Exception {
    MockHttpServletResponse response = send(request);
    assertThat(response.getStatus()).as("the bulk delete is refused").isEqualTo(400);
    assertThat(JsonPath.<String>read(response.getContentAsString(), "$.message"))
        .as("the refusal names the write scope")
        .isEqualTo(WRITE_SCOPE_ERROR);
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
  @DisplayName("A request whose header names one tenant")
  class HeaderNamingOneTenant {

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

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName("given items of another tenant should neither delete nor report them")
    void given_itemsOfAnotherTenant_should_neitherDeleteNorReportThem() throws Exception {
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
        List<String> deletedExplicitly =
            deletedIds(
                bulkDelete(THREAT_ARSENAL_URL, linked, unlinked)
                    .header(TENANT_IDS_HEADER, otherTenantId));
        List<String> deletedByFilter =
            deletedIds(
                bulkDeleteAllLabelled(THREAT_ARSENAL_URL, label)
                    .header(TENANT_IDS_HEADER, otherTenantId));

        // Assert
        assertThat(deletedExplicitly).as("no item is reported by the explicit selection").isEmpty();
        assertThat(deletedByFilter).as("no item is reported by the select-all").isEmpty();
        assertThat(stored(linked)).as("the linked item is kept").isEqualTo(1);
        assertThat(stored(unlinked)).as("the unlinked item is kept").isEqualTo(1);
      } finally {
        remove(linked, unlinked);
      }
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName("given the same item id in both tenants should delete only the named tenant's row")
    void given_theSameItemIdInBothTenants_should_deleteOnlyTheNamedTenantRow() throws Exception {
      // Arrange: the two tenants hold the namesake rows of one onboarded item.
      joinOtherTenant();
      String label = newLabel();
      String sharedId = UUID.randomUUID().toString();
      CommittedItem inDefaultTenant = null;
      CommittedItem inOtherTenant = null;
      try {
        inDefaultTenant = commitUnlinkedItem(Tenant.DEFAULT_TENANT_UUID, label, sharedId);
        inOtherTenant = commitUnlinkedItem(otherTenantId, label, sharedId);

        // Act
        List<String> deleted =
            deletedIds(
                bulkDelete(THREAT_ARSENAL_URL, inOtherTenant)
                    .header(TENANT_IDS_HEADER, otherTenantId));

        // Assert
        assertThat(deleted).as("the item is reported once").containsExactly(sharedId);
        assertThat(stored(inOtherTenant)).as("the named tenant's row is removed").isZero();
        assertThat(stored(inDefaultTenant))
            .as("the namesake row of the other tenant is kept")
            .isEqualTo(1);
      } finally {
        remove(inDefaultTenant, inOtherTenant);
      }
    }
  }

  @Nested
  @DisplayName("A request whose scope does not designate one write tenant")
  class ScopeWithoutOneWriteTenant {

    private String bothTenants() {
      return Tenant.DEFAULT_TENANT_UUID + "," + otherTenantId;
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName("given a header naming two tenants should refuse and keep both namesake rows")
    void given_aHeaderNamingTwoTenants_should_refuseAndKeepBothNamesakeRows() throws Exception {
      // Arrange: one ticked id, and a namesake row in each of the two tenants of the scope.
      joinOtherTenant();
      String label = newLabel();
      String sharedId = UUID.randomUUID().toString();
      CommittedItem inDefaultTenant = null;
      CommittedItem inOtherTenant = null;
      try {
        inDefaultTenant = commitUnlinkedItem(Tenant.DEFAULT_TENANT_UUID, label, sharedId);
        inOtherTenant = commitUnlinkedItem(otherTenantId, label, sharedId);

        // Act
        assertRefusedForWriteScope(
            bulkDelete(THREAT_ARSENAL_URL, inDefaultTenant)
                .header(TENANT_IDS_HEADER, bothTenants()));

        // Assert
        assertThat(stored(inDefaultTenant)).as("the default tenant row is kept").isEqualTo(1);
        assertThat(stored(inOtherTenant)).as("the other tenant row is kept").isEqualTo(1);
      } finally {
        remove(inDefaultTenant, inOtherTenant);
      }
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName("given no header from a multi-tenant caller should refuse and keep every item")
    void given_noHeaderFromAMultiTenantCaller_should_refuseAndKeepEveryItem() throws Exception {
      // Arrange
      joinOtherTenant();
      String label = newLabel();
      CommittedItem inDefaultTenant = null;
      CommittedItem inOtherTenant = null;
      try {
        inDefaultTenant = commitItem(Tenant.DEFAULT_TENANT_UUID, false, label);
        inOtherTenant = commitItem(otherTenantId, false, label);

        // Act
        assertRefusedForWriteScope(bulkDelete(THREAT_ARSENAL_URL, inDefaultTenant, inOtherTenant));
        assertRefusedForWriteScope(bulkDeleteAllLabelled(THREAT_ARSENAL_URL, label));

        // Assert
        assertThat(stored(inDefaultTenant)).as("the default tenant item is kept").isEqualTo(1);
        assertThat(stored(inOtherTenant)).as("the other tenant item is kept").isEqualTo(1);
      } finally {
        remove(inDefaultTenant, inOtherTenant);
      }
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = false)
    @DisplayName("given a caller belonging to no tenant should refuse and keep every item")
    void given_aCallerBelongingToNoTenant_should_refuseAndKeepEveryItem() throws Exception {
      // Arrange
      createOtherTenant();
      String label = newLabel();
      CommittedItem inDefaultTenant = null;
      CommittedItem inOtherTenant = null;
      try {
        inDefaultTenant = commitItem(Tenant.DEFAULT_TENANT_UUID, false, label);
        inOtherTenant = commitItem(otherTenantId, false, label);

        // Act
        assertRefusedForWriteScope(bulkDelete(THREAT_ARSENAL_URL, inDefaultTenant, inOtherTenant));

        // Assert
        assertThat(stored(inDefaultTenant)).as("the default tenant item is kept").isEqualTo(1);
        assertThat(stored(inOtherTenant)).as("the other tenant item is kept").isEqualTo(1);
      } finally {
        remove(inDefaultTenant, inOtherTenant);
      }
    }
  }

  @Nested
  @DisplayName("A request without a selector from a single-tenant caller")
  class SingleTenantCallerWithoutSelector {

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName("given an explicit selection should delete only the unlinked item")
    void given_explicitSelection_should_deleteOnlyTheUnlinkedItem() throws Exception {
      // Arrange: the caller belongs to the default tenant only, so the scope names it alone.
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

  @Nested
  @DisplayName("A caller whose delete right is read in the write tenant")
  class DeleteRightInTheWriteTenant {

    /** The caller sees the items of the other tenant but may not delete them there. */
    private void readOnlyInOtherTenant() {
      joinOtherTenant();
      tenantHelper.grantCapabilitiesInTenant(
          otherTenantId, Set.of(Capability.ACCESS_THREAT_ARSENALS));
    }

    @Test
    @WithMockUser(
        withCapabilities = Capability.DELETE_THREAT_ARSENALS,
        autoJoinDefaultTenant = true)
    @DisplayName("given an explicit selection without the right there should delete nothing")
    void given_explicitSelectionWithoutTheRightThere_should_deleteNothing() throws Exception {
      // Arrange: the right is held in the default tenant, not in the tenant written to.
      readOnlyInOtherTenant();
      String label = newLabel();
      CommittedItem inOtherTenant = null;
      try {
        inOtherTenant = commitItem(otherTenantId, false, label);

        // Act
        List<String> deleted =
            deletedIds(
                bulkDelete(THREAT_ARSENAL_URL, inOtherTenant)
                    .header(TENANT_IDS_HEADER, otherTenantId));

        // Assert
        assertThat(deleted).as("no item is reported").isEmpty();
        assertThat(stored(inOtherTenant)).as("the item is kept").isEqualTo(1);
      } finally {
        remove(inOtherTenant);
      }
    }

    @Test
    @WithMockUser(
        withCapabilities = Capability.DELETE_THREAT_ARSENALS,
        autoJoinDefaultTenant = true)
    @DisplayName("given a select-all request without the right there should delete nothing")
    void given_selectAllWithoutTheRightThere_should_deleteNothing() throws Exception {
      // Arrange
      readOnlyInOtherTenant();
      String label = newLabel();
      CommittedItem inOtherTenant = null;
      try {
        inOtherTenant = commitItem(otherTenantId, false, label);
        assertLabelMatches(otherTenantId, label, 1);

        // Act
        List<String> deleted =
            deletedIds(
                bulkDeleteAllLabelled(THREAT_ARSENAL_URL, label)
                    .header(TENANT_IDS_HEADER, otherTenantId));

        // Assert
        assertThat(deleted).as("no item is reported").isEmpty();
        assertThat(stored(inOtherTenant)).as("the item is kept").isEqualTo(1);
      } finally {
        remove(inOtherTenant);
      }
    }

    @Test
    @WithMockUser(
        withCapabilities = Capability.DELETE_THREAT_ARSENALS,
        autoJoinDefaultTenant = true)
    @DisplayName("given the right in the write tenant should delete its item")
    void given_theRightInTheWriteTenant_should_deleteItsItem() throws Exception {
      // Arrange
      joinOtherTenant();
      tenantHelper.grantCapabilitiesInTenant(
          otherTenantId, Set.of(Capability.DELETE_THREAT_ARSENALS));
      String label = newLabel();
      CommittedItem inOtherTenant = null;
      try {
        inOtherTenant = commitItem(otherTenantId, false, label);

        // Act
        List<String> deleted =
            deletedIds(
                bulkDelete(THREAT_ARSENAL_URL, inOtherTenant)
                    .header(TENANT_IDS_HEADER, otherTenantId));

        // Assert
        assertThat(deleted).as("the item is reported").containsExactly(inOtherTenant.contractId());
        assertThat(stored(inOtherTenant)).as("the item is removed").isZero();
      } finally {
        remove(inOtherTenant);
      }
    }
  }
}
