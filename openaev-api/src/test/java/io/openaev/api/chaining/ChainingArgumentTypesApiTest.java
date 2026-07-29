package io.openaev.api.chaining;

import static io.openaev.rest.settings.PreviewFeature.INJECT_CHAINING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.openaev.database.model.PrimitiveType;
import io.openaev.database.repository.TagRepository;
import io.openaev.rest.custom_dashboard.CustomDashboardService;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.service.PreviewFeatureService;
import io.openaev.service.chaining.ConditionService;
import io.openaev.service.chaining.StepService;
import io.openaev.service.chaining.WorkflowService;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.service.settings.TenantSettingsService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Chaining argument types API")
class ChainingArgumentTypesApiTest {

  @Mock private ExerciseService exerciseService;
  @Mock private CustomDashboardService customDashboardService;
  @Mock private TenantSettingsService tenantSettingsService;
  @Mock private ScenarioService scenarioService;
  @Mock private WorkflowService workflowService;
  @Mock private StepService stepService;
  @Mock private TagRepository tagRepository;
  @Mock private ConditionService conditionService;
  @Mock private PreviewFeatureService previewFeatureService;

  @InjectMocks private ChainingApi chainingApi;

  @Test
  @DisplayName("Should return text/document/targeted-asset when chaining is disabled")
  void given_chainingDisabled_should_returnCoreTypesOnly() {
    when(previewFeatureService.isFeatureEnabled(INJECT_CHAINING)).thenReturn(false);

    List<PrimitiveType> types = chainingApi.getArgumentTypes();

    assertThat(types)
        .containsExactly(PrimitiveType.Text, PrimitiveType.Document, PrimitiveType.TargetedAsset);
  }

  @Test
  @DisplayName("Should return all argument types when chaining is enabled")
  void given_chainingEnabled_should_returnAllTypes() {
    when(previewFeatureService.isFeatureEnabled(INJECT_CHAINING)).thenReturn(true);

    List<PrimitiveType> types = chainingApi.getArgumentTypes();

    assertThat(types).containsExactly(PrimitiveType.values());
    assertThat(types).contains(PrimitiveType.AssetId, PrimitiveType.AssetGroupId);
  }
}
