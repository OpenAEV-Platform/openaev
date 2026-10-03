package io.openaev.service.stix;

import static io.openaev.rest.payload.service.PayloadService.IOC_VALIDATION_FILE_NAME_KEY;
import static io.openaev.rest.payload.service.PayloadService.IOC_VALIDATION_HOST_KEY;
import static io.openaev.rest.payload.service.PayloadService.IOC_VALIDATION_RUN_KEY;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.database.model.IocValidationTestKind;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("IOC validation inject arguments")
class IocValidationInjectArgumentsTest {

  @Test
  @DisplayName("each file drop inject gets its own run directory")
  void given_fileDropPlan_should_addAUniqueRunId() {
    IocValidationPlanner.Plan plan =
        new IocValidationPlanner.Plan(
            IocValidationTestKind.FILE_DROP, Map.of(IOC_VALIDATION_FILE_NAME_KEY, "a.exe"), null);

    Map<String, String> first = IocValidationService.injectArguments(plan);
    Map<String, String> second = IocValidationService.injectArguments(plan);

    assertThat(first).containsEntry(IOC_VALIDATION_FILE_NAME_KEY, "a.exe");
    assertThat(first.get(IOC_VALIDATION_RUN_KEY)).matches("[0-9a-f]{32}");
    assertThat(second.get(IOC_VALIDATION_RUN_KEY)).isNotEqualTo(first.get(IOC_VALIDATION_RUN_KEY));
  }

  @Test
  @DisplayName("the other test kinds keep the arguments of their plan")
  void given_otherPlan_should_keepItsArguments() {
    Map<String, String> arguments = Map.of(IOC_VALIDATION_HOST_KEY, "192.0.2.10");
    IocValidationPlanner.Plan plan =
        new IocValidationPlanner.Plan(IocValidationTestKind.NETWORK_TRAFFIC, arguments, null);

    assertThat(IocValidationService.injectArguments(plan)).isEqualTo(arguments);
  }
}
