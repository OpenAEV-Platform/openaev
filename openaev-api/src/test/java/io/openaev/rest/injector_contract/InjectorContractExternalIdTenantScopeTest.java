package io.openaev.rest.injector_contract;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Injector;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.InjectorContractId;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Pins the shape of {@code injector_contract_external_id} uniqueness, within a tenant and across
 * tenants.
 *
 * <p>{@code InjectorContract}'s primary key is composite {@code (injector_contract_id, tenant_id)},
 * so every tenant holds its own copy of a contract and two tenants legitimately share contract ids
 * (built-in contracts are registered per tenant from the same declarations). The external id column
 * does not follow that rule: it carries a global unique index, so the same external id can exist
 * once on the whole platform.
 *
 * <p>The first test records today's behaviour: the second tenant's insert is rejected. It is the
 * test to flip if the index is ever made tenant-scoped. The second test records the part that must
 * hold either way, so a migration cannot drop uniqueness altogether instead of narrowing it.
 *
 * <p>Deliberately NOT {@code @Transactional}: the constraint is only evaluated when the insert
 * reaches PostgreSQL, and the first row has to be committed before the second one is written.
 */
@WithMockUser(isAdmin = true)
@DisplayName("Injector contract external id uniqueness across tenants")
class InjectorContractExternalIdTenantScopeTest extends IntegrationTest {

  private static final String SHARED_EXTERNAL_ID = "shared-external-id";

  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private InjectorRepository injectorRepository;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private EntityManager entityManager;

  private String tenantAId;
  private String tenantBId;
  private String injectorAId;
  private String injectorBId;
  private String contractAId;
  private String contractBId;

  @BeforeEach
  void createTwoTenants() throws Exception {
    tenantAId = tenantHelper.createTenantWithCurrentUser("ic-external-id-a").getId();
    tenantBId = tenantHelper.createTenantWithCurrentUser("ic-external-id-b").getId();
  }

  @AfterEach
  void cleanup() {
    deleteContract(tenantAId, contractAId, injectorAId);
    deleteContract(tenantBId, contractBId, injectorBId);
  }

  @Test
  @DisplayName("A second tenant cannot hold an external id another tenant already uses")
  void given_anExternalIdUsedByAnotherTenant_should_rejectTheInsert() {
    // Arrange
    contractAId = persistContractWithExternalId(tenantAId, SHARED_EXTERNAL_ID, true);

    // Act + Assert
    assertThatThrownBy(() -> persistContractWithExternalId(tenantBId, SHARED_EXTERNAL_ID, false))
        .rootCause()
        .hasMessageContaining("injectors_contracts_injector_contract_external_id_key")
        .hasMessageContaining(SHARED_EXTERNAL_ID);
  }

  @Test
  @DisplayName("One tenant cannot hold the same external id twice")
  void given_anExternalIdUsedInTheSameTenant_should_rejectTheInsert() {
    // Arrange
    contractAId = persistContractWithExternalId(tenantAId, SHARED_EXTERNAL_ID, true);

    // Act + Assert
    assertThatThrownBy(() -> persistContractWithExternalId(tenantAId, SHARED_EXTERNAL_ID, false))
        .rootCause()
        .hasMessageContaining(SHARED_EXTERNAL_ID);
  }

  private String persistContractWithExternalId(
      String tenantId, String externalId, boolean isTenantA) {
    return tenantTx.execute(
        TxCtx.forTenant(tenantId),
        () -> {
          Injector injector = InjectorFixture.createDefaultPayloadInjector();
          injector.setTenantId(tenantId);
          injector.setCreatedAt(Instant.now());
          injector.setUpdatedAt(Instant.now());
          Injector savedInjector = injectorRepository.save(injector);
          if (isTenantA) {
            injectorAId = savedInjector.getId();
          } else {
            injectorBId = savedInjector.getId();
          }

          InjectorContract contract =
              InjectorContractFixture.createDefaultInjectorContractWithExternalId(externalId);
          contract.setId(UUID.randomUUID().toString());
          contract.setTenant(new Tenant(tenantId));
          contract.clearInjectors();
          contract.addInjector(savedInjector);
          InjectorContract saved = injectorContractRepository.save(contract);
          entityManager.flush();
          String savedId = saved.getId();
          if (isTenantA) {
            contractAId = savedId;
          } else {
            contractBId = savedId;
          }
          return savedId;
        });
  }

  private void deleteContract(String tenantId, String contractId, String injectorId) {
    if (tenantId == null) {
      return;
    }
    tenantTx.execute(
        TxCtx.forTenant(tenantId),
        () -> {
          if (contractId != null) {
            entityManager
                .createNativeQuery(
                    "DELETE FROM injectors_injector_contracts WHERE injector_contract_id ="
                        + " :contractId")
                .setParameter("contractId", contractId)
                .executeUpdate();
            injectorContractRepository
                .findById(new InjectorContractId(contractId, tenantId))
                .ifPresent(injectorContractRepository::delete);
          }
          if (injectorId != null) {
            injectorRepository.deleteByInjectorId(injectorId);
          }
        });
  }
}
