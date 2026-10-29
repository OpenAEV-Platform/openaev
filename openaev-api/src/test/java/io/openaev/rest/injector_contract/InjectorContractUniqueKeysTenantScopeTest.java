package io.openaev.rest.injector_contract;

import static io.openaev.migration.V6_20261014160000000__Make_injector_contract_unique_indexes_tenant_aware.EXTERNAL_ID_INDEX;
import static io.openaev.migration.V6_20261014160000000__Make_injector_contract_unique_indexes_tenant_aware.PAYLOAD_INDEX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Injector;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.InjectorContractId;
import io.openaev.database.model.Payload;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.PayloadFixture;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Pins the shape of the unique keys of {@code injectors_contracts} on the real database, within a
 * tenant and across tenants.
 *
 * <p>{@code InjectorContract}'s primary key is composite {@code (injector_contract_id, tenant_id)},
 * so its business keys are unique per tenant: a second tenant may hold the same external id or the
 * same payload, one tenant may not hold either twice. The second half matters as much as the first:
 * it is what keeps the per-tenant indexes from being dropped instead of narrowed.
 *
 * <p>Deliberately NOT {@code @Transactional}: a constraint is only evaluated when the insert
 * reaches PostgreSQL, and the first row has to be committed before the second one is written.
 */
@WithMockUser(isAdmin = true)
@DisplayName("Injector contract unique keys across tenants")
class InjectorContractUniqueKeysTenantScopeTest extends IntegrationTest {

  private static final String SHARED_EXTERNAL_ID = "shared-external-id";

  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private InjectorRepository injectorRepository;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private PayloadRepository payloadRepository;
  @Autowired private EntityManager entityManager;

  private String tenantAId;
  private String tenantBId;
  private String payloadAId;
  private final List<InjectorContractId> contractIds = new ArrayList<>();
  private final List<TenantRow> injectors = new ArrayList<>();

  private record TenantRow(String id, String tenantId) {}

  @BeforeEach
  void createTwoTenants() throws Exception {
    tenantAId = tenantHelper.createTenantWithCurrentUser("ic-unique-keys-a").getId();
    tenantBId = tenantHelper.createTenantWithCurrentUser("ic-unique-keys-b").getId();
  }

  @AfterEach
  void cleanup() {
    contractIds.forEach(this::deleteContract);
    injectors.forEach(
        injector ->
            tenantTx.execute(
                TxCtx.forTenant(injector.tenantId()),
                () -> {
                  injectorRepository.deleteByInjectorId(injector.id());
                }));
    if (payloadAId != null) {
      tenantTx.execute(
          TxCtx.forTenant(tenantAId),
          () -> {
            payloadRepository.deleteById(payloadAId);
          });
    }
    contractIds.clear();
    injectors.clear();
    payloadAId = null;
    // Not @Transactional, so the tenants are committed: remove them after the table-specific
    // cleanup, or every later class in the fork inherits them.
    tenantHelper.deleteCommittedTenants(tenantAId, tenantBId);
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("External id")
  class ExternalId {

    @Test
    @DisplayName("A second tenant can hold an external id another tenant already uses")
    void given_anExternalIdUsedByAnotherTenant_should_acceptTheInsert() {
      // Arrange
      persistContract(tenantAId, SHARED_EXTERNAL_ID, null);

      // Act
      persistContract(tenantBId, SHARED_EXTERNAL_ID, null);

      // Assert
      assertThat(countContractsWithExternalId(SHARED_EXTERNAL_ID)).isEqualTo(2);
    }

    @Test
    @DisplayName("One tenant cannot hold the same external id twice")
    void given_anExternalIdUsedInTheSameTenant_should_rejectTheInsert() {
      // Arrange
      persistContract(tenantAId, SHARED_EXTERNAL_ID, null);

      // Act + Assert
      assertThatThrownBy(() -> persistContract(tenantAId, SHARED_EXTERNAL_ID, null))
          .rootCause()
          .hasMessageContaining(EXTERNAL_ID_INDEX)
          .hasMessageContaining(SHARED_EXTERNAL_ID);
    }
  }

  @Nested
  @DisplayName("Payload")
  class PayloadKey {

    @Test
    @DisplayName("The payload index does not refuse a second tenant's contract on the same payload")
    void given_aPayloadBackingAnotherTenantsContract_should_acceptTheInsert() {
      // Arrange
      payloadAId = persistPayload(tenantAId);
      persistContract(tenantAId, null, payloadAId);

      // Act
      persistContract(tenantBId, null, payloadAId);

      // Assert
      assertThat(countContractsWithPayload(payloadAId)).isEqualTo(2);
    }

    @Test
    @DisplayName("One tenant cannot back two contracts with the same payload")
    void given_aPayloadBackingAContractOfTheSameTenant_should_rejectTheInsert() {
      // Arrange
      payloadAId = persistPayload(tenantAId);
      persistContract(tenantAId, null, payloadAId);

      // Act + Assert
      assertThatThrownBy(() -> persistContract(tenantAId, null, payloadAId))
          .rootCause()
          .hasMessageContaining(PAYLOAD_INDEX)
          .hasMessageContaining(payloadAId);
    }
  }

  private String persistPayload(String tenantId) {
    return tenantTx.execute(
        TxCtx.forTenant(tenantId),
        () -> {
          Payload payload = PayloadFixture.createDefaultCommand();
          payload.setTenant(new Tenant(tenantId));
          return payloadRepository.save(payload).getId();
        });
  }

  private void persistContract(String tenantId, String externalId, String payloadId) {
    tenantTx.execute(
        TxCtx.forTenant(tenantId),
        () -> {
          Injector injector = InjectorFixture.createDefaultPayloadInjector();
          injector.setTenantId(tenantId);
          injector.setCreatedAt(Instant.now());
          injector.setUpdatedAt(Instant.now());
          Injector savedInjector = injectorRepository.save(injector);
          injectors.add(new TenantRow(savedInjector.getId(), tenantId));

          InjectorContract contract =
              InjectorContractFixture.createDefaultInjectorContractWithExternalId(externalId);
          contract.setId(UUID.randomUUID().toString());
          contract.setTenant(new Tenant(tenantId));
          if (payloadId != null) {
            contract.setPayload(entityManager.getReference(Payload.class, payloadId));
          }
          contract.clearInjectors();
          contract.addInjector(savedInjector);
          InjectorContract saved = injectorContractRepository.save(contract);
          entityManager.flush();
          contractIds.add(new InjectorContractId(saved.getId(), tenantId));
        });
  }

  private long countContractsWithExternalId(String externalId) {
    return countNative(
        "SELECT count(*) FROM injectors_contracts WHERE injector_contract_external_id = :key",
        externalId);
  }

  private long countContractsWithPayload(String payloadId) {
    return countNative(
        "SELECT count(*) FROM injectors_contracts WHERE injector_contract_payload = :key",
        payloadId);
  }

  private long countNative(String sql, String key) {
    return tenantTx.execute(
        TxCtx.allTenants(),
        () ->
            ((Number)
                    entityManager.createNativeQuery(sql).setParameter("key", key).getSingleResult())
                .longValue());
  }

  private void deleteContract(InjectorContractId contract) {
    tenantTx.execute(
        TxCtx.forTenant(contract.getTenantId()),
        () -> {
          entityManager
              .createNativeQuery(
                  "DELETE FROM injectors_injector_contracts WHERE injector_contract_id = :contractId"
                      + " AND tenant_id = :tenantId")
              .setParameter("contractId", contract.getId())
              .setParameter("tenantId", contract.getTenantId())
              .executeUpdate();
          injectorContractRepository
              .findById(contract)
              .ifPresent(injectorContractRepository::delete);
        });
  }
}
