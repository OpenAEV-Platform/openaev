package io.openaev.rest.injector_contract;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.ProductionTablesIntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Removing a link row of an injector contract bumps the contract's {@code
 * injector_contract_updated_at}, so the search index picks the contract up again. The bump is done
 * by a database trigger on the attack pattern and vulnerability link tables, and a contract id is
 * shared by every tenant: the trigger has to bump the copy of the tenant that lost the link and
 * leave the copies of the other tenants alone.
 *
 * <p>Deliberately NOT {@code @Transactional}: the rows of both tenants have to be committed before
 * the scoped transactions write and read them, and the trigger fires inside the deleting
 * transaction.
 */
@WithMockUser(isAdmin = true)
@DisplayName("Updated timestamp of an injector contract held by several tenants")
class InjectorContractUpdatedAtTriggerTest extends ProductionTablesIntegrationTest {

  private static final String PAST_TIMESTAMP = "2000-01-01 00:00:00";

  enum LinkTable {
    ATTACK_PATTERNS("injectors_contracts_attack_patterns"),
    VULNERABILITIES("injectors_contracts_vulnerabilities");

    final String table;

    LinkTable(String table) {
      this.table = table;
    }
  }

  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private EntityManager entityManager;

  private final String sharedContractId = UUID.randomUUID().toString();
  private String tenantA;
  private String tenantB;

  @BeforeEach
  void seedTheSameContractIdInTwoTenants() throws Exception {
    tenantA = tenantHelper.createTenant("ic-updated-at-a").getId();
    tenantB = tenantHelper.createTenant("ic-updated-at-b").getId();
    seedContractWithLinks(tenantA);
    seedContractWithLinks(tenantB);
  }

  @AfterEach
  void cleanup() {
    // Every row seeded here cascades with its tenant
    tenantHelper.deleteCommittedTenants(tenantA, tenantB);
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("A link row removed by tenant A")
  class RemovedByTenantA {

    @ParameterizedTest(name = "{0}")
    @EnumSource(LinkTable.class)
    @DisplayName("given_linkRowRemovedByTenantA_should_leaveTheCopyOfTenantBUntouched")
    void given_linkRowRemovedByTenantA_should_leaveTheCopyOfTenantBUntouched(LinkTable link) {
      // Arrange
      String before = updatedAtOf(tenantB);

      // Act
      removeLinkRowOfTenantA(link);

      // Assert
      assertThat(updatedAtOf(tenantB)).isEqualTo(before);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(LinkTable.class)
    @DisplayName("given_linkRowRemovedByTenantA_should_bumpTheCopyOfTenantA")
    void given_linkRowRemovedByTenantA_should_bumpTheCopyOfTenantA(LinkTable link) {
      // Arrange
      String before = updatedAtOf(tenantA);

      // Act
      removeLinkRowOfTenantA(link);

      // Assert
      assertThat(updatedAtOf(tenantA)).isNotEqualTo(before);
    }
  }

  private void removeLinkRowOfTenantA(LinkTable link) {
    tenantTx.execute(
        TxCtx.forTenant(tenantA),
        () ->
            entityManager
                .createNativeQuery(
                    "DELETE FROM "
                        + link.table
                        + " WHERE injector_contract_id = :contract AND tenant_id = :tenant")
                .setParameter("contract", sharedContractId)
                .setParameter("tenant", tenantA)
                .executeUpdate());
  }

  private String updatedAtOf(String tenantId) {
    Object updatedAt =
        tenantTx.execute(
            TxCtx.forTenant(tenantId),
            () ->
                entityManager
                    .createNativeQuery(
                        "SELECT CAST(injector_contract_updated_at AS text) FROM injectors_contracts"
                            + " WHERE injector_contract_id = :contract AND tenant_id = :tenant")
                    .setParameter("contract", sharedContractId)
                    .setParameter("tenant", tenantId)
                    .getSingleResult());
    return String.valueOf(updatedAt);
  }

  private void seedContractWithLinks(String tenantId) {
    tenantTx.execute(
        TxCtx.forTenant(tenantId),
        () -> {
          String attackPatternId = UUID.randomUUID().toString();
          String vulnerabilityId = UUID.randomUUID().toString();
          execute(
              "INSERT INTO attack_patterns (attack_pattern_id, attack_pattern_stix_id,"
                  + " attack_pattern_name, attack_pattern_external_id, tenant_id) VALUES (?1, ?2,"
                  + " ?3, ?4, ?5)",
              attackPatternId,
              "attack-pattern--" + attackPatternId,
              "ic-updated-at-pattern-" + attackPatternId,
              "T9" + attackPatternId.substring(0, 6),
              tenantId);
          execute(
              "INSERT INTO vulnerabilities (vulnerability_id, tenant_id,"
                  + " vulnerability_external_id) VALUES (?1, ?2, ?3)",
              vulnerabilityId,
              tenantId,
              "CVE-9999-" + vulnerabilityId.substring(0, 8));
          execute(
              "INSERT INTO injectors_contracts (injector_contract_id, tenant_id,"
                  + " injector_contract_content, injector_contract_import_available) VALUES (?1,"
                  + " ?2, '{}', false)",
              sharedContractId,
              tenantId);
          execute(
              "INSERT INTO injectors_contracts_attack_patterns (injector_contract_id,"
                  + " attack_pattern_id, tenant_id) VALUES (?1, ?2, ?3)",
              sharedContractId,
              attackPatternId,
              tenantId);
          execute(
              "INSERT INTO injectors_contracts_vulnerabilities (injector_contract_id,"
                  + " vulnerability_id, tenant_id) VALUES (?1, ?2, ?3)",
              sharedContractId,
              vulnerabilityId,
              tenantId);
          entityManager
              .createNativeQuery(
                  "UPDATE injectors_contracts SET injector_contract_updated_at = '"
                      + PAST_TIMESTAMP
                      + "' WHERE injector_contract_id = :contract AND tenant_id = :tenant")
              .setParameter("contract", sharedContractId)
              .setParameter("tenant", tenantId)
              .executeUpdate();
          entityManager.flush();
        });
  }

  private void execute(String sql, String... parameters) {
    var query = entityManager.createNativeQuery(sql);
    for (int i = 0; i < parameters.length; i++) {
      query.setParameter(i + 1, parameters[i]);
    }
    query.executeUpdate();
  }
}
