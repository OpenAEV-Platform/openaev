package io.openaev.service.stix;

import static io.openaev.rest.payload.service.PayloadService.IOC_VALIDATION_FILE_NAME_KEY;
import static io.openaev.rest.payload.service.PayloadService.IOC_VALIDATION_HOST_KEY;
import static io.openaev.rest.payload.service.PayloadService.IOC_VALIDATION_RUN_KEY;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.IocValidationTestKind;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("IOC validation inject arguments")
class IocValidationInjectArgumentsTest {

  @Test
  @DisplayName("each approved file drop gets its own run seed, which its fingerprint covers")
  void given_fileDropPlan_should_addARunSeedItsFingerprintCovers() {
    IocValidationPlanner.Plan plan =
        new IocValidationPlanner.Plan(
            IocValidationTestKind.FILE_DROP, Map.of(IOC_VALIDATION_FILE_NAME_KEY, "a.exe"), null);

    IocValidationPlanner.Plan first = IocValidationService.approvedPlan(plan);
    IocValidationPlanner.Plan second = IocValidationService.approvedPlan(plan);

    assertThat(first.arguments()).containsEntry(IOC_VALIDATION_FILE_NAME_KEY, "a.exe");
    assertThat(first.arguments().get(IOC_VALIDATION_RUN_KEY)).matches("[0-9a-f]{32}");
    assertThat(second.arguments().get(IOC_VALIDATION_RUN_KEY))
        .isNotEqualTo(first.arguments().get(IOC_VALIDATION_RUN_KEY));
    // What the dispatch reads back from an inject: another valid seed no longer matches
    ObjectNode content = JsonNodeFactory.instance.objectNode();
    first.arguments().forEach(content::put);
    String approved = IocValidationPlanner.fingerprint(first);
    assertThat(IocValidationPlanner.fingerprintOf(IocValidationTestKind.FILE_DROP, content))
        .contains(approved);
    content.put(IOC_VALIDATION_RUN_KEY, second.arguments().get(IOC_VALIDATION_RUN_KEY));
    assertThat(IocValidationPlanner.fingerprintOf(IocValidationTestKind.FILE_DROP, content))
        .isPresent()
        .get()
        .isNotEqualTo(approved);
  }

  @Test
  @DisplayName("the other test kinds keep the plan they were approved with")
  void given_otherPlan_should_keepItsArguments() {
    Map<String, String> arguments = Map.of(IOC_VALIDATION_HOST_KEY, "192.0.2.10");
    IocValidationPlanner.Plan plan =
        new IocValidationPlanner.Plan(IocValidationTestKind.NETWORK_TRAFFIC, arguments, null);

    assertThat(IocValidationService.approvedPlan(plan)).isSameAs(plan);
  }
}
