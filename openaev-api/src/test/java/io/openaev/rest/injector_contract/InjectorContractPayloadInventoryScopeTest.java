package io.openaev.rest.injector_contract;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The payload-delete path inventories the contracts a payload backs before the database cascade
 * removes them. Once {@code injectors_contracts} is tenant-isolated, that inventory reads only the
 * tenants in scope: a contract of another tenant pointing at the same payload is not returned.
 *
 * <p>The tests arm the production list plus {@code injectors_contracts}. Not
 * {@code @Transactional}: the reads run in the scoped primitive, which refuses an active
 * transaction, so rows are committed through JDBC and removed after each test.
 */
@WithMockUser(isAdmin = true)
@DisplayName("Inventory of the contracts backed by a payload")
class InjectorContractPayloadInventoryScopeTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTablesWithInjectorContracts(DynamicPropertyRegistry registry) {
    TreeSet<String> tables = new TreeSet<>(ProductionActiveTables.names());
    tables.add("injectors_contracts");
    registry.add("openaev.tenant.active-tables", () -> String.join(",", tables));
  }

  @Autowired private InjectorContractService injectorContractService;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private String payloadTenantId;
  private String otherTenantId;
  private String payloadId;
  private String payloadContractId;
  private String otherContractId;

  @BeforeEach
  void seedAPayloadBackingContractsInTwoTenants() {
    jdbc = new JdbcTemplate(dataSource);
    payloadTenantId = insertTenant("payload-inventory-owner");
    otherTenantId = insertTenant("payload-inventory-other");
    payloadId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO payloads (payload_id, payload_type, payload_name, tenant_id)"
            + " VALUES (?, 'Command', 'payload-inventory', ?)",
        payloadId,
        payloadTenantId);
    payloadContractId = insertContract(payloadTenantId);
    otherContractId = insertContract(otherTenantId);
  }

  @AfterEach
  void sweep() {
    jdbc.update("DELETE FROM injectors_contracts WHERE injector_contract_payload = ?", payloadId);
    jdbc.update("DELETE FROM payloads WHERE payload_id = ?", payloadId);
    jdbc.update("DELETE FROM tenants WHERE tenant_id IN (?, ?)", payloadTenantId, otherTenantId);
  }

  private String insertTenant(String name) {
    String tenantId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        tenantId,
        name + "-" + tenantId);
    return tenantId;
  }

  private String insertContract(String tenantId) {
    String contractId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO injectors_contracts (injector_contract_id, injector_contract_content,"
            + " injector_contract_payload, tenant_id) VALUES (?, '{}', ?, ?)",
        contractId,
        payloadId,
        tenantId);
    return contractId;
  }

  @Nested
  @DisplayName("findContractIdsByPayloadIdPerTenant")
  class FindContractIdsByPayloadIdPerTenant {

    @Test
    @DisplayName("given the payload's tenant in scope, should return its contract only")
    void given_payloadTenantInScope_should_returnItsContractOnly() {
      // Act
      Map<String, List<String>> inventory =
          tenantTx.execute(
              TxCtx.forTenant(payloadTenantId),
              () -> injectorContractService.findContractIdsByPayloadIdPerTenant(payloadId));

      // Assert
      assertThat(inventory).isEqualTo(Map.of(payloadTenantId, List.of(payloadContractId)));
    }

    @Test
    @DisplayName("given both tenants in scope, should return both contracts")
    void given_bothTenantsInScope_should_returnBothContracts() {
      // Act
      Map<String, List<String>> inventory =
          tenantTx.execute(
              TxCtx.forTenants(List.of(payloadTenantId, otherTenantId)),
              () -> injectorContractService.findContractIdsByPayloadIdPerTenant(payloadId));

      // Assert
      assertThat(inventory)
          .isEqualTo(
              Map.of(
                  payloadTenantId, List.of(payloadContractId),
                  otherTenantId, List.of(otherContractId)));
    }
  }
}
