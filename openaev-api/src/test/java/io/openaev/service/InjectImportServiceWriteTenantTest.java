package io.openaev.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.context.TxCtx;
import io.openaev.database.model.ImportMapper;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.rest.scenario.response.ImportTestSummary;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.XlsFixture;
import io.openaev.utils.fixtures.import_mapper.ImportMapperFixture;
import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * The XLS importer resolves the tenant it writes in once ({@code importXls}) and stamps the teams
 * it creates with it. An imported inject takes the same tenant: {@code InjectImportService} sets it
 * on the inject it builds, and {@code InjectUtils#attachToScenario} or {@code
 * InjectUtils#attachToExercise} sets the target's tenant when it attaches the inject, so {@code
 * TenantBaseListener} finds the tenant set and keeps it. The ambient tenant, which the non-prefixed
 * route leaves on the platform default, plays no part, while the contract the inject points at
 * belongs to the target's tenant.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Importing injects from an XLS file writes them in the target's tenant")
class InjectImportServiceWriteTenantTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private InjectImportService injectImportService;
  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  @AfterEach
  void clearContext() {
    TenantContext.clearCurrentTenant();
  }

  @Test
  @DisplayName("given_noAmbientTenantAndScenarioInAnotherTenant_should_stampInjectWithItsTenant")
  void given_noAmbientTenantAndScenarioInAnotherTenant_should_stampInjectWithItsTenant()
      throws Exception {
    // -- ARRANGE --
    String otherTenantId = tenantHelper.createTenantWithCurrentUser("xls-import-write-b").getId();
    Scenario scenario = ScenarioFixture.createDefaultCrisisScenario();
    scenario.setTenant(new Tenant(otherTenantId));
    scenario = scenarioRepository.save(scenario);

    String importId = XlsFixture.createDefaultXlsFile();
    ImportMapper importMapper =
        ImportMapperFixture.createImportMapper(XlsFixture.DEFAULT_INJECT_TYPE);

    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);

    // -- ACT --
    // Not saved: the stamp the importer sets is what is read back, before any listener could
    // supply the ambient one.
    ImportTestSummary result =
        injectImportService.importInjectIntoScenarioFromXLS(
            TxCtx.forTenant(otherTenantId),
            scenario,
            importMapper,
            importId,
            XlsFixture.DEFAULT_SHEET_NAME,
            0,
            false);

    // -- ASSERT --
    assertThat(result.getInjects()).hasSize(1);
    assertThat(result.getInjects().getFirst().getTenant().getId()).isEqualTo(otherTenantId);
  }
}
