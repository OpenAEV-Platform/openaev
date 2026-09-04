package io.openaev.rest.scenario;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.Scenario;
import io.openaev.ee.EnterpriseEditionException;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.ee.License;
import io.openaev.rest.exception.ChainingException;
import io.openaev.service.chaining.WorkflowService;
import io.openaev.service.scenario.ScenarioService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScenarioApi - duplicate")
class ScenarioApiDuplicateUnitTest {

  private static final String SCENARIO_ID = "scenario-id";

  @Mock private ScenarioService scenarioService;
  @Mock private WorkflowService workflowService;
  @Mock private EnterpriseEditionService enterpriseEditionService;
  @Mock private LicenseCacheManager licenseCacheManager;

  @InjectMocks private ScenarioApi scenarioApi;

  @Nested
  @DisplayName("Given a time-based scenario")
  class GivenATimeBasedScenario {

    @Test
    @DisplayName("Given a scenario with no workflow, should duplicate metadata only")
    void given_a_scenario_with_no_workflow_should_duplicate_metadata_only()
        throws ChainingException {
      // -- ARRANGE --
      Scenario duplicate = new Scenario();
      when(scenarioService.getDuplicateScenario(SCENARIO_ID)).thenReturn(duplicate);
      when(workflowService.isScenarioChaining(SCENARIO_ID)).thenReturn(false);

      // -- ACT --
      Scenario result = scenarioApi.duplicateScenario(SCENARIO_ID);

      // -- ASSERT --
      assertSame(duplicate, result);
      verify(workflowService, never()).duplicateScenarioWorkflow(anyString(), any(Scenario.class));
      // The licence is only relevant to the chained branch: a time-based scenario must stay
      // duplicable on a Community platform.
      verifyNoInteractions(enterpriseEditionService, licenseCacheManager);
    }
  }

  @Nested
  @DisplayName("Given a chained scenario")
  class GivenAChainedScenario {

    @Test
    @DisplayName("Given an active license, should also duplicate the logic map")
    void given_an_active_license_should_also_duplicate_the_logic_map() throws ChainingException {
      // -- ARRANGE --
      Scenario duplicate = new Scenario();
      License license = new License();
      when(scenarioService.getDuplicateScenario(SCENARIO_ID)).thenReturn(duplicate);
      when(workflowService.isScenarioChaining(SCENARIO_ID)).thenReturn(true);
      when(licenseCacheManager.getEnterpriseEditionInfo()).thenReturn(license);
      when(enterpriseEditionService.isEnterpriseLicenseInactive(license)).thenReturn(false);

      // -- ACT --
      Scenario result = scenarioApi.duplicateScenario(SCENARIO_ID);

      // -- ASSERT --
      assertSame(duplicate, result);
      verify(workflowService).duplicateScenarioWorkflow(SCENARIO_ID, duplicate);
    }

    @Test
    @DisplayName("Given an inactive license, should reject instead of losing the logic map")
    void given_an_inactive_license_should_reject_instead_of_losing_the_logic_map()
        throws ChainingException {
      // -- ARRANGE --
      License license = new License();
      when(scenarioService.getDuplicateScenario(SCENARIO_ID)).thenReturn(new Scenario());
      when(workflowService.isScenarioChaining(SCENARIO_ID)).thenReturn(true);
      when(licenseCacheManager.getEnterpriseEditionInfo()).thenReturn(license);
      when(enterpriseEditionService.isEnterpriseLicenseInactive(license)).thenReturn(true);

      // -- ACT & ASSERT --
      assertThrows(
          EnterpriseEditionException.class, () -> scenarioApi.duplicateScenario(SCENARIO_ID));
      verify(workflowService, never()).duplicateScenarioWorkflow(anyString(), any(Scenario.class));
    }
  }
}
