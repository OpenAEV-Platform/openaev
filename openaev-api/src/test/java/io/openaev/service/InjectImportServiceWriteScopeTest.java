package io.openaev.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * The XLS inject importer resolves the write tenant of an import from the target scenario and
 * stamps the teams it creates with it. The imported injects take that same tenant, not the ambient
 * one, which is the default tenant on the non-prefixed route.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Importing injects from an XLS file writes them in the target's tenant")
class InjectImportServiceWriteScopeTest extends IntegrationTest {

  @Autowired private InjectImportService injectImportService;
  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  @AfterEach
  void clearContext() {
    TenantContext.clearCurrentTenant();
  }

  @Test
  @DisplayName("given_noAmbientTenantAndScenarioInB_should_writeImportedInjectInB")
  void given_noAmbientTenantAndScenarioInB_should_writeImportedInjectInB() throws Exception {
    // Arrange
    String tenantB = tenantHelper.createTenantWithCurrentUser("xls-import-scope-b").getId();
    Scenario scenario = ScenarioFixture.createDefaultCrisisScenario();
    scenario.setTenant(new Tenant(tenantB));
    scenario = scenarioRepository.save(scenario);
    String importId = XlsFixture.createDefaultXlsFile();
    ImportMapper importMapper =
        ImportMapperFixture.createImportMapper(XlsFixture.DEFAULT_INJECT_TYPE);
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);

    // Act
    ImportTestSummary result =
        injectImportService.importInjectIntoScenarioFromXLS(
            TxCtx.forTenant(tenantB),
            scenario,
            importMapper,
            importId,
            XlsFixture.DEFAULT_SHEET_NAME,
            0,
            true);

    // Assert
    assertThat(result.getTotalNumberOfInjects()).isEqualTo(1);
    assertThat(result.getInjects()).hasSize(1);
    assertThat(result.getInjects().getFirst().getTenant().getId()).isEqualTo(tenantB);
  }
}
