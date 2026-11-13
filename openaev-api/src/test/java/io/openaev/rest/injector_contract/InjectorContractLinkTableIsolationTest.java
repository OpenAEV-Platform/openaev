package io.openaev.rest.injector_contract;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.ProductionTablesIntegrationTest;
import io.openaev.architecture.CoversTenantIsolation;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Injector;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The link rows of an injector contract that another tenant holds are neither read nor written by a
 * caller scoped to one tenant. A contract id is shared by every tenant, so each tenant holds its
 * own link rows for the same id, and only the tenant column tells them apart.
 *
 * <p>Each case runs on one link table of the production list: the contract's tags, attack patterns,
 * security domains, vulnerabilities and injectors. The queries are plain statements with no tenant
 * predicate of their own, so what keeps tenant B's row out is the statement inspector alone.
 *
 * <p>Deliberately NOT {@code @Transactional}: the rows of both tenants have to be committed before
 * the scoped transactions read them.
 */
@CoversTenantIsolation({
  "injector_contract_tags",
  "injectors_contracts_attack_patterns",
  "injectors_contracts_domains",
  "injectors_contracts_vulnerabilities",
  "injectors_injector_contracts"
})
@WithMockUser(isAdmin = true)
@DisplayName("Link rows of an injector contract across tenants")
class InjectorContractLinkTableIsolationTest extends ProductionTablesIntegrationTest {

  enum LinkTable {
    TAGS("injector_contract_tags"),
    ATTACK_PATTERNS("injectors_contracts_attack_patterns"),
    DOMAINS("injectors_contracts_domains"),
    VULNERABILITIES("injectors_contracts_vulnerabilities"),
    INJECTORS("injectors_injector_contracts");

    final String table;

    LinkTable(String table) {
      this.table = table;
    }
  }

  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private InjectorRepository injectorRepository;
  @Autowired private EntityManager entityManager;

  private final String sharedContractId = UUID.randomUUID().toString();
  private String tenantA;
  private String tenantB;

  @BeforeEach
  void seedTheSameContractIdInTwoTenants() throws Exception {
    tenantA = tenantHelper.createTenant("ic-link-a").getId();
    tenantB = tenantHelper.createTenant("ic-link-b").getId();
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
  @DisplayName("A caller scoped to tenant A")
  class ScopedToTenantA {

    @ParameterizedTest(name = "{0}")
    @EnumSource(LinkTable.class)
    @DisplayName("given_sameContractInTwoTenants_should_readOnlyTheLinkRowOfTenantA")
    void given_sameContractInTwoTenants_should_readOnlyTheLinkRowOfTenantA(LinkTable link) {
      // Act
      List<String> tenants =
          tenantTx.execute(
              TxCtx.forTenant(tenantA),
              () ->
                  entityManager
                      .createNativeQuery(
                          "SELECT tenant_id FROM "
                              + link.table
                              + " WHERE injector_contract_id = :contract")
                      .setParameter("contract", sharedContractId)
                      .getResultStream()
                      .map(String::valueOf)
                      .toList());

      // Assert
      assertThat(tenants).containsExactly(tenantA);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(LinkTable.class)
    @DisplayName("given_linkRowOfTenantB_should_notReadItEvenNamedByItsTenant")
    void given_linkRowOfTenantB_should_notReadItEvenNamedByItsTenant(LinkTable link) {
      // Act
      long seenByA = countOfTenant(link, tenantA, tenantB);
      long seenByB = countOfTenant(link, tenantB, tenantB);

      // Assert: the row exists, and only its own tenant reads it
      assertThat(seenByB).isEqualTo(1);
      assertThat(seenByA).isZero();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(LinkTable.class)
    @DisplayName("given_linkRowOfTenantB_should_deleteNothingOfTenantB")
    void given_linkRowOfTenantB_should_deleteNothingOfTenantB(LinkTable link) {
      // Act
      int deleted =
          tenantTx.execute(
              TxCtx.forTenant(tenantA),
              () ->
                  entityManager
                      .createNativeQuery(
                          "DELETE FROM "
                              + link.table
                              + " WHERE injector_contract_id = :contract AND tenant_id = :tenant")
                      .setParameter("contract", sharedContractId)
                      .setParameter("tenant", tenantB)
                      .executeUpdate());

      // Assert
      assertThat(deleted).isZero();
      assertThat(countOfTenant(link, tenantB, tenantB)).isEqualTo(1);
    }
  }

  private long countOfTenant(LinkTable link, String scopedTenant, String rowTenant) {
    Object count =
        tenantTx.execute(
            TxCtx.forTenant(scopedTenant),
            () ->
                entityManager
                    .createNativeQuery(
                        "SELECT count(*) FROM "
                            + link.table
                            + " WHERE injector_contract_id = :contract AND tenant_id = :tenant")
                    .setParameter("contract", sharedContractId)
                    .setParameter("tenant", rowTenant)
                    .getSingleResult());
    return ((Number) count).longValue();
  }

  private void seedContractWithLinks(String tenantId) {
    tenantTx.execute(
        TxCtx.forTenant(tenantId),
        () -> {
          Injector injector = InjectorFixture.createDefaultPayloadInjector();
          injector.setTenantId(tenantId);
          injector.setCreatedAt(Instant.now());
          injector.setUpdatedAt(Instant.now());
          String injectorId = injectorRepository.save(injector).getId();

          String tagId = UUID.randomUUID().toString();
          String attackPatternId = UUID.randomUUID().toString();
          String domainId = UUID.randomUUID().toString();
          String vulnerabilityId = UUID.randomUUID().toString();
          execute(
              "INSERT INTO tags (tag_id, tag_name, tag_color, tenant_id) VALUES (?1, ?2, '#111111',"
                  + " ?3)",
              tagId,
              "ic-link-tag-" + tagId,
              tenantId);
          execute(
              "INSERT INTO attack_patterns (attack_pattern_id, attack_pattern_stix_id,"
                  + " attack_pattern_name, attack_pattern_external_id, tenant_id) VALUES (?1, ?2,"
                  + " ?3, ?4, ?5)",
              attackPatternId,
              "attack-pattern--" + attackPatternId,
              "ic-link-pattern-" + attackPatternId,
              "T9" + attackPatternId.substring(0, 6),
              tenantId);
          execute(
              "INSERT INTO domains (domain_id, domain_name, tenant_id) VALUES (?1, ?2, ?3)",
              domainId,
              "ic-link-domain-" + domainId,
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
              "INSERT INTO injector_contract_tags (injector_contract_id, tag_id, tenant_id) VALUES"
                  + " (?1, ?2, ?3)",
              sharedContractId,
              tagId,
              tenantId);
          execute(
              "INSERT INTO injectors_contracts_attack_patterns (injector_contract_id,"
                  + " attack_pattern_id, tenant_id) VALUES (?1, ?2, ?3)",
              sharedContractId,
              attackPatternId,
              tenantId);
          execute(
              "INSERT INTO injectors_contracts_domains (injector_contract_id, domain_id, tenant_id)"
                  + " VALUES (?1, ?2, ?3)",
              sharedContractId,
              domainId,
              tenantId);
          execute(
              "INSERT INTO injectors_contracts_vulnerabilities (injector_contract_id,"
                  + " vulnerability_id, tenant_id) VALUES (?1, ?2, ?3)",
              sharedContractId,
              vulnerabilityId,
              tenantId);
          execute(
              "INSERT INTO injectors_injector_contracts (injector_id, injector_contract_id,"
                  + " tenant_id) VALUES (?1, ?2, ?3)",
              injectorId,
              sharedContractId,
              tenantId);
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
