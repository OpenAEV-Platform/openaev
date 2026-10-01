package io.openaev.rest.injector_contract;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.CollectorType;
import io.openaev.database.model.Injector;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.InjectorContractId;
import io.openaev.database.model.Payload;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.CollectorTypeRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.PayloadFixture;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.persistence.EntityManager;
import java.time.Instant;
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
 * With {@code collector_types} v2-activated, {@code InjectorContractService}'s tuple queries (FULL
 * and THREAT_ARSENAL) project {@code collector_type.name} while grouping only by {@code
 * collector_type.id} ({@code getCommonGroupBy}). {@code TenantStatementInspector} wraps the joined
 * {@code collector_types} in a filtered derived table, which has no primary key, so PostgreSQL
 * rejects the statement: the selected {@code name} column is no longer functionally dependent on
 * the grouped {@code id}. This surfaces as a 500 on {@code POST /injector_contracts/search} (full
 * details) and on both {@code /threat_arsenals/search} routes.
 *
 * <p>Deliberately NOT {@code @Transactional}, same shape as {@code PayloadCollectorTypeScopeTest}:
 * a test-managed transaction changes how the tenant scope is applied to the request and was found
 * to mask this exact defect.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=collector_types")
@WithMockUser(isAdmin = true)
@DisplayName("InjectorContractApi/ThreatArsenalApi search with collector_types active")
class InjectorContractCollectorTypeGroupByTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private CollectorTypeRepository collectorTypeRepository;
  @Autowired private PayloadRepository payloadRepository;
  @Autowired private InjectorRepository injectorRepository;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private EntityManager entityManager;

  private String tenantId;
  private String collectorTypeId;
  private String payloadId;
  private String injectorId;
  private String injectorContractId;

  @BeforeEach
  void seedContractWithPayloadCollectorType() throws Exception {
    tenantId = tenantHelper.createTenantWithCurrentUser("ic-collector-type-groupby").getId();

    tenantTx.execute(
        TxCtx.forTenant(tenantId),
        () -> {
          CollectorType collectorType = new CollectorType();
          collectorType.setName("openaev_groupby_test_" + UUID.randomUUID());
          collectorType.setTenant(new Tenant(tenantId));
          CollectorType savedCollectorType = collectorTypeRepository.save(collectorType);
          collectorTypeId = savedCollectorType.getId();

          Payload payload = PayloadFixture.createDefaultCommand();
          payload.setTenant(new Tenant(tenantId));
          payload.setCollectorType(savedCollectorType);
          Payload savedPayload = payloadRepository.save(payload);
          payloadId = savedPayload.getId();

          Injector injector = InjectorFixture.createDefaultPayloadInjector();
          injector.setTenantId(tenantId);
          injector.setCreatedAt(Instant.now());
          injector.setUpdatedAt(Instant.now());
          Injector savedInjector = injectorRepository.save(injector);
          injectorId = savedInjector.getId();

          InjectorContract injectorContract =
              InjectorContractFixture.createDefaultInjectorContract();
          injectorContract.setTenant(new Tenant(tenantId));
          injectorContract.setPayload(savedPayload);
          injectorContract.clearInjectors();
          injectorContract.addInjector(savedInjector);
          InjectorContract savedContract = injectorContractRepository.save(injectorContract);
          injectorContractId = savedContract.getId();
        });
  }

  @AfterEach
  void cleanup() {
    tenantTx.execute(
        TxCtx.forTenant(tenantId),
        () -> {
          // Direct query, no entity loading, same shape as
          // InjectorContractService#deleteInjectorContractById: the contract and the injector's
          // own cascade would otherwise race to delete this join row twice.
          entityManager
              .createNativeQuery(
                  "DELETE FROM injectors_injector_contracts WHERE injector_contract_id = :contractId"
                      + " AND injector_id = :injectorId")
              .setParameter("contractId", injectorContractId)
              .setParameter("injectorId", injectorId)
              .executeUpdate();
          injectorContractRepository.deleteById(
              new InjectorContractId(injectorContractId, tenantId));
          payloadRepository.deleteById(payloadId);
          injectorRepository.deleteByInjectorId(injectorId);
          collectorTypeRepository.deleteById(collectorTypeId);
        });
  }

  @Test
  @DisplayName(
      "POST /injector_contracts/search with full details does not 500 once collector_types is"
          + " active")
  void given_collectorTypeActive_should_notFailOnInjectorContractSearch() throws Exception {
    SearchPaginationInput input = new SearchPaginationInput();

    mvc.perform(
            post("/api/tenants/{tenantId}/injector_contracts/search", tenantId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input)))
        .andExpect(status().is2xxSuccessful());
  }

  @Test
  @DisplayName("POST /threat_arsenals/search does not 500 once collector_types is active")
  void given_collectorTypeActive_should_notFailOnThreatArsenalSearch() throws Exception {
    SearchPaginationInput input = new SearchPaginationInput();

    mvc.perform(
            post("/api/tenants/{tenantId}/threat_arsenals/search", tenantId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input)))
        .andExpect(status().is2xxSuccessful());
  }
}
