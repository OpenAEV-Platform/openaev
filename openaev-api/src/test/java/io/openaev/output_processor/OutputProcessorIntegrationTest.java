package io.openaev.output_processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import io.openaev.IntegrationTest;
import io.openaev.database.model.ContractOutputType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;

@TestInstance(PER_CLASS)
@DisplayName("Integration tests for OutputProcessor loading and context support")
class OutputProcessorIntegrationTest extends IntegrationTest {

  @Autowired private OutputProcessorFactory registry;

  @Test
  void shouldLoadAllHandlersFromSpring() {
    for (ContractOutputType type : ContractOutputType.values()) {
      OutputProcessor handler = registry.getProcessor(type);

      assertThat(handler).withFailMessage("Handler not found for type: " + type).isNotNull();
    }
  }

  @Test
  void shouldReturnCorrectHandlerForEachType() {
    assertThat(registry.getProcessor(ContractOutputType.Text))
        .isInstanceOf(TextOutputProcessor.class);

    assertThat(registry.getProcessor(ContractOutputType.PortsScan))
        .isInstanceOf(PortScanOutputProcessor.class);

    assertThat(registry.getProcessor(ContractOutputType.CVE))
        .isInstanceOf(CVEOutputProcessor.class);
  }

  @Test
  void shouldReturnSameInstanceOnMultipleCalls() {
    OutputProcessor handler1 = registry.getProcessor(ContractOutputType.Text);
    OutputProcessor handler2 = registry.getProcessor(ContractOutputType.Text);

    assertThat(handler1).isSameAs(handler2);
  }
}
