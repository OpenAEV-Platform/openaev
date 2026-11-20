package io.openaev.service.scenario;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.openaev.api.catalog.CatalogFeature;
import io.openaev.context.TxCtx;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.service.catalog.scenario.targeting.TargetChoice;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Launching a scenario and the catalogue feature")
class ScenarioLaunchServiceTest {

  private static final String SCENARIO_ID = "scenario-id";
  private static final TxCtx CTX = TxCtx.forTenant("tenant-1");

  @Mock private ScenarioService scenarioService;
  @Mock private CatalogFeature catalogFeature;

  @InjectMocks private ScenarioLaunchService scenarioLaunchService;

  @Test
  @DisplayName(
      "given targets while the catalogue is off, should refuse before reading the scenario")
  void given_targetsAndCatalogueOff_should_refuseTheLaunch() {
    // Arrange
    doThrow(new ElementNotFoundException("The catalogue feature is disabled"))
        .when(catalogFeature)
        .require();
    TargetChoice targets = new TargetChoice(List.of("asset-id"), List.of(), List.of(), List.of());

    // Act & Assert
    assertThatThrownBy(() -> scenarioLaunchService.launch(CTX, SCENARIO_ID, targets))
        .isInstanceOf(ElementNotFoundException.class);
    verifyNoInteractions(scenarioService);
  }

  @Test
  @DisplayName("given no target, should launch without asking for the catalogue")
  void given_noTarget_should_notAskForTheCatalogue() {
    // Arrange
    when(scenarioService.scenario(CTX, SCENARIO_ID)).thenThrow(new ElementNotFoundException());

    // Act & Assert
    assertThatThrownBy(() -> scenarioLaunchService.launch(CTX, SCENARIO_ID, TargetChoice.NONE))
        .isInstanceOf(ElementNotFoundException.class);
    verify(catalogFeature, never()).require();
  }
}
