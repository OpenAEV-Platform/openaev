package io.openaev.service.stix;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.database.model.IocValidation;
import io.openaev.database.model.IocValidationIoc;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("IOC validation simulation injects")
class IocValidationSimulationInjectsTest {

  @Test
  @DisplayName("every IOC tracks the simulation injects copied from its scenario injects")
  void given_launchedSimulation_should_trackTheSimulationInjects() {
    IocValidationIoc tested = ioc(List.of("scenario-1", "scenario-2"));
    IocValidationIoc skipped = ioc(List.of());
    IocValidation validation = validation(tested, skipped);
    List<IocValidationIoc> before = validation.getIocs();

    IocValidationService.trackSimulationInjects(
        validation,
        Map.of(
            "scenario-1", "simulation-1", "scenario-2", "simulation-2", "other", "simulation-3"));

    assertThat(validation.getIocs().get(0).getInjectIds())
        .containsExactly("simulation-1", "simulation-2");
    assertThat(validation.getIocs().get(1).getInjectIds()).isEmpty();
    // A new list makes the change visible to the dirty checking of the JSON column
    assertThat(validation.getIocs()).isNotSameAs(before);
  }

  @Test
  @DisplayName("a scenario inject with no simulation copy stops the approval")
  void given_scenarioInjectWithoutCopy_should_fail() {
    IocValidation validation = validation(ioc(List.of("scenario-1")));

    assertThatThrownBy(
            () ->
                IocValidationService.trackSimulationInjects(
                    validation, Map.of("scenario-2", "simulation-2")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("scenario-1");
  }

  private static IocValidationIoc ioc(List<String> injectIds) {
    IocValidationIoc ioc = new IocValidationIoc();
    ioc.setInjectIds(new ArrayList<>(injectIds));
    return ioc;
  }

  private static IocValidation validation(IocValidationIoc... iocs) {
    IocValidation validation = new IocValidation();
    validation.setIocs(new ArrayList<>(List.of(iocs)));
    return validation;
  }
}
