package io.openaev.rest.custom_dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.context.TxCtx;
import io.openaev.database.model.CustomDashboard;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Setting;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.TenantSettingKeys;
import io.openaev.database.repository.CustomDashboardRepository;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.rest.dashboard.DashboardService;
import io.openaev.service.settings.TenantSettingsService;
import io.openaev.utils.mapper.CustomDashboardMapper;
import jakarta.persistence.EntityNotFoundException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Default dashboard of a simulation or scenario, resolved in the request scope")
class CustomDashboardServiceResourceScopeTest {

  private static final String REQUEST_TENANT = "request-tenant";
  private static final String OTHER_TENANT = "other-tenant";
  private static final String RESOURCE_ID = "resource-id";

  @Mock private CustomDashboardRepository customDashboardRepository;
  @Mock private CustomDashboardMapper customDashboardMapper;
  @Mock private TenantSettingsService tenantSettingsService;
  @Mock private DashboardService dashboardService;
  @Mock private ScenarioRepository scenarioRepository;
  @Mock private ExerciseRepository exerciseRepository;

  private final TxCtx ctx = TxCtx.forTenant(REQUEST_TENANT);

  private CustomDashboardService service() {
    return new CustomDashboardService(
        customDashboardRepository,
        customDashboardMapper,
        tenantSettingsService,
        dashboardService,
        scenarioRepository,
        exerciseRepository);
  }

  private static Tenant tenant(String id) {
    return new Tenant(id);
  }

  @Nested
  @DisplayName("Simulation")
  class SimulationResource {

    @Test
    @DisplayName("given a simulation outside the request scope, its tenant settings are not read")
    void given_simulationOutsideTheRequestScope_should_notReadItsTenantSettings() {
      // Arrange
      Exercise exercise = new Exercise();
      exercise.setTenant(tenant(OTHER_TENANT));
      when(customDashboardRepository.findByResourceId(RESOURCE_ID)).thenReturn(Optional.empty());
      when(scenarioRepository.findById(RESOURCE_ID)).thenReturn(Optional.empty());
      when(exerciseRepository.findById(RESOURCE_ID)).thenReturn(Optional.of(exercise));

      // Act & Assert
      assertThatThrownBy(() -> service().findCustomDashboardByResourceId(ctx, RESOURCE_ID))
          .isInstanceOf(EntityNotFoundException.class);
      verify(tenantSettingsService, never()).findSetting(anyString(), anyString());
    }

    @Test
    @DisplayName(
        "given a simulation of the request scope, the default dashboard of its tenant is read")
    void given_simulationOfTheRequestScope_should_readTheDefaultDashboardOfItsTenant() {
      // Arrange
      Exercise exercise = new Exercise();
      exercise.setTenant(tenant(REQUEST_TENANT));
      Setting setting = new Setting();
      setting.setValue("dashboard-id");
      CustomDashboard dashboard = new CustomDashboard();
      when(customDashboardRepository.findByResourceId(RESOURCE_ID)).thenReturn(Optional.empty());
      when(scenarioRepository.findById(RESOURCE_ID)).thenReturn(Optional.empty());
      when(exerciseRepository.findById(RESOURCE_ID)).thenReturn(Optional.of(exercise));
      when(tenantSettingsService.findSetting(
              REQUEST_TENANT, TenantSettingKeys.TENANT_SIMULATION_DASHBOARD.key()))
          .thenReturn(Optional.of(setting));
      when(customDashboardRepository.findById("dashboard-id")).thenReturn(Optional.of(dashboard));

      // Act
      CustomDashboard result = service().findCustomDashboardByResourceId(ctx, RESOURCE_ID);

      // Assert
      assertThat(result).isSameAs(dashboard);
    }
  }

  @Nested
  @DisplayName("Scenario")
  class ScenarioResource {

    @Test
    @DisplayName("given a scenario outside the request scope, its tenant settings are not read")
    void given_scenarioOutsideTheRequestScope_should_notReadItsTenantSettings() {
      // Arrange
      Scenario scenario = new Scenario();
      scenario.setTenant(tenant(OTHER_TENANT));
      when(customDashboardRepository.findByResourceId(RESOURCE_ID)).thenReturn(Optional.empty());
      when(scenarioRepository.findById(RESOURCE_ID)).thenReturn(Optional.of(scenario));
      when(exerciseRepository.findById(RESOURCE_ID)).thenReturn(Optional.empty());

      // Act & Assert
      assertThatThrownBy(() -> service().findCustomDashboardByResourceId(ctx, RESOURCE_ID))
          .isInstanceOf(EntityNotFoundException.class);
      verify(tenantSettingsService, never()).findSetting(anyString(), anyString());
      verify(customDashboardRepository, never()).findById(any());
    }
  }
}
