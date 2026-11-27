package io.openaev.api.bulk_operation;

import static io.openaev.api.bulk_operation.BulkOperationApi.BULK_OPERATION_URI;
import static io.openaev.api.bulk_operation.BulkOperationApi.TENANT_BULK_OPERATION_URI;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TxCtx;
import io.openaev.service.utils.BulkOperationMonitor;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * An operation launched on a scope of several tenants carries no tenant. Its launcher lists it on
 * every route, whatever tenant the route names, and no other user lists it.
 */
@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WithMockUser
@DisplayName("Bulk operations of the caller")
class BulkOperationApiTest extends IntegrationTest {

  private static final String PREFIXED_ROUTE = "prefixed";
  private static final String NON_PREFIXED_ROUTE = "non-prefixed";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private BulkOperationMonitor bulkOperationMonitor;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String tenantA;
  private String tenantB;

  @BeforeEach
  void seedCallerOfTwoTenants() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("bulk-operation-tenant-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("bulk-operation-tenant-b").getId();
  }

  @Nested
  @DisplayName("Operation with no tenant")
  class OperationWithNoTenant {

    @ParameterizedTest(name = "{0} route")
    @ValueSource(strings = {NON_PREFIXED_ROUTE, PREFIXED_ROUTE})
    @DisplayName("given a live operation of the caller with no tenant should list it")
    void given_liveOperationOfTheCallerWithNoTenant_should_listIt(String route) throws Exception {
      // Arrange
      String operationId =
          bulkOperationMonitor.start(
              TxCtx.forTenants(List.of(tenantA, tenantB)), "delete", "teams", 2);

      // Act
      String response = listOn(route);

      // Assert
      assertThatJson(response).inPath("$[*].bulk_operation_id").isArray().contains(operationId);
    }

    @ParameterizedTest(name = "{0} route")
    @ValueSource(strings = {NON_PREFIXED_ROUTE, PREFIXED_ROUTE})
    @DisplayName("given a journaled operation of the caller with no tenant should list it")
    void given_journaledOperationOfTheCallerWithNoTenant_should_listIt(String route)
        throws Exception {
      // Arrange
      String operationId = journal(testUserHolder.get().getId());

      // Act
      String response = listOn(route);

      // Assert
      assertThatJson(response).inPath("$[*].bulk_operation_id").isArray().contains(operationId);
    }

    @ParameterizedTest(name = "{0} route")
    @ValueSource(strings = {NON_PREFIXED_ROUTE, PREFIXED_ROUTE})
    @DisplayName("given a journaled operation of another user with no tenant should not list it")
    void given_journaledOperationOfAnotherUserWithNoTenant_should_notListIt(String route)
        throws Exception {
      // Arrange
      String operationId = journal("bulk-operation-other-user-" + UUID.randomUUID());

      // Act
      String response = listOn(route);

      // Assert
      assertThatJson(response)
          .inPath("$[*].bulk_operation_id")
          .isArray()
          .doesNotContain(operationId);
    }
  }

  private String listOn(String route) throws Exception {
    String uri =
        NON_PREFIXED_ROUTE.equals(route)
            ? BULK_OPERATION_URI
            : TENANT_BULK_OPERATION_URI.replace("{tenantId}", tenantA);
    return mvc.perform(get(uri))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  /** An operation of a node that restarted: in the journal, not in memory. */
  private String journal(String userId) {
    String operationId = UUID.randomUUID().toString();
    jdbcTemplate.update(
        "INSERT INTO bulk_operations (operation_id, operation_action, operation_entity,"
            + " operation_total, operation_processed, operation_status, operation_tenant_id,"
            + " operation_user_id) VALUES (?, 'delete', 'teams', 2, 2, 'COMPLETED', NULL, ?)",
        operationId,
        userId);
    return operationId;
  }
}
