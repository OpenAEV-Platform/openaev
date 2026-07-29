package io.openaev.api.chaining;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.openaev.api.chaining.dto.StepOutput;
import io.openaev.database.model.Step;
import io.openaev.database.model.StepStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class StepMapperTest {

  @Test
  void given_stepDataWithNativeInjectorOutputsInContent_should_extractOutputTypesFromContent() {
    // -- Arrange --
    Step step = new Step();
    step.setStatus(StepStatus.TEMPLATE);
    step.setData(
        """
        {
          "inject_injector_contract": {
            "injector_contract_id": "contract-id",
            "injector_contract_content": "{\\"outputs\\":[{\\"type\\":\\"portscan\\"}]}"
          }
        }
        """);

    // -- Act --
    StepOutput output = StepMapper.toOutput(step);

    // -- Assert --
    assertEquals(List.of("port"), output.getOutputTypes());
  }
}
