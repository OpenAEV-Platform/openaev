package io.openaev.rest.payload;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.CollectorType;
import io.openaev.database.model.Payload;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.CollectorTypeRepository;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.fixtures.PayloadFixture;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * With {@code collector_types} v2-activated, {@code POST /api/payloads/search} returns {@code
 * Page<Payload>} straight from the controller, and {@code
 * io.openaev.helper.CollectorTypeNameSerializer} calls {@code getName()} on the lazy {@code
 * Payload.collectorType} proxy. That call happens during Jackson serialization of the HTTP
 * response, after the {@code @Transactional} controller method has already returned and the scope
 * it opened has closed, so the lazy load runs with no tenant scope and fails closed.
 *
 * <p>Deliberately NOT {@code @Transactional}: the whole point of this defect is the gap between the
 * controller's transaction and the serialization that happens after it, and a test-managed
 * transaction wrapping the entire MockMvc call would hide it by keeping the scope open through
 * serialization. Seed and clean through auto-committed saves, same shape as {@code
 * CollectorServiceMultiTenantScopeTest} and {@code HostedPublicApiTest}.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=collector_types")
@WithMockUser(isAdmin = true)
@DisplayName("PayloadApi#payloads serializes the collector type name outside the request scope")
class PayloadCollectorTypeScopeTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private CollectorTypeRepository collectorTypeRepository;
  @Autowired private PayloadRepository payloadRepository;

  private String tenantId;
  private String payloadId;
  private String collectorTypeId;
  private String collectorTypeName;

  @BeforeEach
  void seedPayloadWithCollectorType() throws Exception {
    tenantId = tenantHelper.createTenantWithCurrentUser("payload-collector-type-scope").getId();
    collectorTypeName = "openaev_scope_test_" + UUID.randomUUID();

    // Both saves in one transaction, scoped to the owning tenant: the collector type must stay
    // MANAGED (not detached) when the payload references it, or the FK resolution at flush is
    // itself a second, scoped read - the write-path failure this task's brief warns about.
    tenantTx.execute(
        TxCtx.forTenant(tenantId),
        () -> {
          CollectorType collectorType = new CollectorType();
          collectorType.setName(collectorTypeName);
          collectorType.setTenant(new Tenant(tenantId));
          CollectorType savedCollectorType = collectorTypeRepository.save(collectorType);
          collectorTypeId = savedCollectorType.getId();

          Payload payload = PayloadFixture.createDefaultCommand();
          payload.setTenant(new Tenant(tenantId));
          payload.setCollectorType(savedCollectorType);
          payloadId = payloadRepository.save(payload).getId();
        });
  }

  @AfterEach
  void cleanup() {
    // The delete goes through ModelBaseListener's audit trail, which reads collectorType for its
    // own change record - same lazy load, needs the same scope a real DELETE request would carry.
    tenantTx.execute(
        TxCtx.forTenant(tenantId),
        () -> {
          payloadRepository.deleteById(payloadId);
          collectorTypeRepository.deleteById(collectorTypeId);
        });
  }

  @Test
  @DisplayName(
      "searching payloads under the owning tenant's path returns the collector type name, not a"
          + " fail-closed 5xx")
  void given_payloadWithCollectorType_should_serializeCollectorTypeNameOnSearch() throws Exception {
    // Arrange
    SearchPaginationInput searchPaginationInput = PaginationFixture.getDefault().build();

    // Act & Assert
    mvc.perform(
            post("/api/tenants/{tenantId}/payloads/search", tenantId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(searchPaginationInput)))
        .andExpect(status().is2xxSuccessful())
        .andExpect(
            jsonPath("$.content[?(@.payload_id=='" + payloadId + "')].payload_collector_type")
                .value(collectorTypeName));
  }
}
