package io.openaev.rest.injector_contract;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Tenant;
import io.openaev.multitenancy.DependenciesManagerException;
import io.openaev.rest.domain.DomainService;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Tenant provisioning copies the default tenant's built-in injector contracts into the new tenant.
 * Tenant creation runs in the caller's transaction, scoped to the caller's tenants: it never
 * includes the new tenant, and includes the default tenant only for its members. The copy must not
 * depend on who creates the tenant, and must never link the new tenant's contracts to rows of the
 * default tenant.
 *
 * <p>The tests arm the production list plus {@code injectors_contracts}. Not
 * {@code @Transactional}: the default templates are read in a nested transaction that sees
 * committed rows only, so the onboarding runs in a committed transaction of its own and is removed
 * after each test.
 */
@ExtendWith(OutputCaptureExtension.class)
@WithMockUser(isAdmin = true)
@DisplayName("Provisioning the default injector contracts of a new tenant")
class DefaultInjectorContractProvisioningTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTablesWithInjectorContracts(DynamicPropertyRegistry registry) {
    TreeSet<String> tables = new TreeSet<>(ProductionActiveTables.names());
    tables.add("injectors_contracts");
    registry.add("openaev.tenant.active-tables", () -> String.join(",", tables));
  }

  /** A preset domain: every tenant is provisioned with one of that name. */
  private static final String PRESET_DOMAIN = "Tabletop";

  @Autowired private InjectorContractService injectorContractService;
  @Autowired private DomainService domainService;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private EntityManager entityManager;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private String callerTenantId;
  private Tenant newTenant;
  private String templateId;
  private String defaultTagId;
  private String defaultDomainId;
  private boolean defaultDomainSeeded;

  /**
   * A template of the default tenant, linked to the default tenant's preset domain and to a tag
   * that only the default tenant holds. The test harness does not register the built-in injectors
   * of the default tenant, so the real built-in templates are not relied on.
   */
  @BeforeEach
  void seedATemplateInTheDefaultTenant() {
    jdbc = new JdbcTemplate(dataSource);
    callerTenantId = insertTenant("provisioning-caller");
    newTenant = new Tenant(insertTenant("provisioning-new"));
    templateId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO injectors_contracts (injector_contract_id, injector_contract_content,"
            + " tenant_id) VALUES (?, '{}', ?)",
        templateId,
        DEFAULT_TENANT_UUID);
    List<String> presetDomain =
        jdbc.queryForList(
            "SELECT domain_id FROM domains WHERE tenant_id = ? AND domain_name = ?",
            String.class,
            DEFAULT_TENANT_UUID,
            PRESET_DOMAIN);
    defaultDomainSeeded = presetDomain.isEmpty();
    defaultDomainId = defaultDomainSeeded ? UUID.randomUUID().toString() : presetDomain.getFirst();
    if (defaultDomainSeeded) {
      jdbc.update(
          "INSERT INTO domains (domain_id, domain_name, domain_color, domain_created_at,"
              + " domain_updated_at, tenant_id) VALUES (?, ?, '#FFCC33', now(), now(), ?)",
          defaultDomainId,
          PRESET_DOMAIN,
          DEFAULT_TENANT_UUID);
    }
    jdbc.update(
        "INSERT INTO injectors_contracts_domains (injector_contract_id, tenant_id, domain_id)"
            + " VALUES (?, ?, ?)",
        templateId,
        DEFAULT_TENANT_UUID,
        defaultDomainId);
    defaultTagId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tags (tag_id, tag_name, tag_color, tag_created_at, tag_updated_at, tenant_id)"
            + " VALUES (?, ?, '#000000', now(), now(), ?)",
        defaultTagId,
        "provisioning-default-only-" + defaultTagId,
        DEFAULT_TENANT_UUID);
    jdbc.update(
        "INSERT INTO injector_contract_tags (injector_contract_id, tenant_id, tag_id)"
            + " VALUES (?, ?, ?)",
        templateId,
        DEFAULT_TENANT_UUID,
        defaultTagId);
  }

  @AfterEach
  void sweep() {
    for (String table : List.of("injectors_contracts_domains", "injector_contract_tags")) {
      jdbc.update(
          "DELETE FROM " + table + " WHERE injector_contract_id = ? OR tenant_id = ?",
          templateId,
          newTenant.getId());
    }
    jdbc.update(
        "DELETE FROM injectors_contracts WHERE injector_contract_id = ? OR tenant_id = ?",
        templateId,
        newTenant.getId());
    jdbc.update("DELETE FROM tags WHERE tag_id = ?", defaultTagId);
    if (defaultDomainSeeded) {
      jdbc.update("DELETE FROM domains WHERE domain_id = ?", defaultDomainId);
    }
    jdbc.update("DELETE FROM domains WHERE tenant_id = ?", newTenant.getId());
    jdbc.update("DELETE FROM tenants WHERE tenant_id IN (?, ?)", newTenant.getId(), callerTenantId);
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

  @FunctionalInterface
  private interface Provisioning {
    void run() throws DependenciesManagerException;
  }

  /**
   * Runs the provisioning managers in order, in one transaction scoped like the caller's request,
   * and returns the scope the transaction carries once they are done.
   */
  private String provisionAs(TxCtx callerScope, Provisioning contracts) {
    return tenantTx.execute(
        callerScope,
        () -> {
          try {
            domainService.createDependencyForTenant(newTenant);
            contracts.run();
          } catch (DependenciesManagerException e) {
            throw new IllegalStateException(e);
          }
          return (String)
              entityManager
                  .createNativeQuery("SELECT current_setting('app.current_tenants', true)")
                  .setFlushMode(FlushModeType.COMMIT)
                  .getSingleResult();
        });
  }

  private String copyTemplateAs(TxCtx callerScope) {
    return provisionAs(
        callerScope,
        () ->
            injectorContractService.copyDefaultContracts(
                newTenant, List.of(templateId), List.of()));
  }

  private List<String> newTenantContracts() {
    return jdbc.queryForList(
        "SELECT injector_contract_id FROM injectors_contracts WHERE tenant_id = ?",
        String.class,
        newTenant.getId());
  }

  @Nested
  @DisplayName("copyDefaultContracts")
  class CopyDefaultContracts {

    @Test
    @DisplayName("given a caller outside the default tenant, should copy the default template")
    void given_callerOutsideTheDefaultTenant_should_copyTheDefaultTemplate() {
      // Act
      copyTemplateAs(TxCtx.forTenant(callerTenantId));

      // Assert
      assertThat(newTenantContracts()).containsExactly(templateId);
    }

    @Test
    @DisplayName(
        "given a template linked to a preset domain, should link the copy to the new tenant's"
            + " domain of that name")
    void given_templateLinkedToAPresetDomain_should_linkTheCopyToTheNewTenantDomain() {
      // Act: a default member, so the template's own links are visible to the caller
      copyTemplateAs(TxCtx.forTenants(List.of(DEFAULT_TENANT_UUID, callerTenantId)));

      // Assert
      assertThat(
              jdbc.queryForList(
                  "SELECT d.tenant_id || ':' || d.domain_name FROM injectors_contracts_domains icd"
                      + " JOIN domains d ON d.domain_id = icd.domain_id"
                      + " WHERE icd.injector_contract_id = ? AND icd.tenant_id = ?",
                  String.class,
                  templateId,
                  newTenant.getId()))
          .containsExactly(newTenant.getId() + ":" + PRESET_DOMAIN);
    }

    @Test
    @DisplayName(
        "given a template tagged in the default tenant only, should copy it without the tag")
    void given_templateTaggedInTheDefaultTenantOnly_should_copyItWithoutTheTag() {
      // Act
      copyTemplateAs(TxCtx.forTenants(List.of(DEFAULT_TENANT_UUID, callerTenantId)));

      // Assert: no counterpart in the new tenant, so no link, never the default tenant's tag
      assertThat(newTenantContracts()).containsExactly(templateId);
      assertThat(
              jdbc.queryForList(
                  "SELECT tag_id FROM injector_contract_tags WHERE tenant_id = ?",
                  String.class,
                  newTenant.getId()))
          .isEmpty();
    }

    @Test
    @DisplayName(
        "given a required template the default tenant lacks, should log it as an error and copy"
            + " the others")
    void given_requiredTemplateMissing_should_logItAsAnErrorAndCopyTheOthers(
        CapturedOutput output) {
      // Arrange
      String missingRequired = UUID.randomUUID().toString();
      String missingOptional = UUID.randomUUID().toString();

      // Act
      provisionAs(
          TxCtx.forTenant(callerTenantId),
          () ->
              injectorContractService.copyDefaultContracts(
                  newTenant, List.of(templateId, missingRequired), List.of(missingOptional)));

      // Assert
      assertThat(newTenantContracts()).containsExactly(templateId);
      assertThat(output.getOut().lines().filter(line -> line.contains(" ERROR ")))
          .anyMatch(line -> line.contains(missingRequired))
          .noneMatch(line -> line.contains(missingOptional));
    }
  }

  @Nested
  @DisplayName("createDependencyForTenant")
  class CreateDependencyForTenant {

    @Test
    @DisplayName(
        "given the provisioning is done, should leave the transaction scoped to the new tenant")
    void given_provisioningDone_should_leaveTheTransactionScopedToTheNewTenant() {
      // Act
      String scopeAfter =
          provisionAs(
              TxCtx.forTenant(callerTenantId),
              () -> injectorContractService.createDependencyForTenant(newTenant));

      // Assert: the default scope of the template read never reaches the managers that follow
      assertThat(scopeAfter).isEqualTo(newTenant.getId());
    }
  }
}
