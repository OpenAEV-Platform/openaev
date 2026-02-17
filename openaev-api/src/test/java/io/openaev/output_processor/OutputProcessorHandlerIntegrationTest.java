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
@DisplayName("Integration tests for OutputProcessorHandler loading and context support")
class OutputProcessorHandlerIntegrationTest extends IntegrationTest {

  @Autowired private OutputProcessorHandlerRegistry registry;

  @Test
  void shouldLoadAllHandlersFromSpring() {
    for (ContractOutputType type : ContractOutputType.values()) {
      OutputProcessorHandler handler = registry.getHandler(type);

      assertThat(handler).withFailMessage("Handler not found for type: " + type).isNotNull();
    }
  }

  @Test
  void shouldReturnCorrectHandlerForEachType() {
    assertThat(registry.getHandler(ContractOutputType.Text))
        .isInstanceOf(TextOutputProcessorHandler.class);

    assertThat(registry.getHandler(ContractOutputType.PortsScan))
        .isInstanceOf(PortScanOutputProcessorHandler.class);

    assertThat(registry.getHandler(ContractOutputType.CVE))
        .isInstanceOf(CVEOutputProcessorHandler.class);
  }

  @Test
  void shouldReturnSameInstanceOnMultipleCalls() {
    OutputProcessorHandler handler1 = registry.getHandler(ContractOutputType.Text);
    OutputProcessorHandler handler2 = registry.getHandler(ContractOutputType.Text);

    assertThat(handler1).isSameAs(handler2);
  }

  @Test
  void shouldHaveCorrectContextSupport() {
    OutputProcessorHandler handler = registry.getHandler(ContractOutputType.Text);
    assertThat(handler.getSupportedContexts()).contains(ProcessingContext.FINDING);
    handler = registry.getHandler(ContractOutputType.Port);
    assertThat(handler.getSupportedContexts()).contains(ProcessingContext.FINDING);
    handler = registry.getHandler(ContractOutputType.Number);
    assertThat(handler.getSupportedContexts()).contains(ProcessingContext.FINDING);
    handler = registry.getHandler(ContractOutputType.PortsScan);
    assertThat(handler.getSupportedContexts()).contains(ProcessingContext.FINDING);
    handler = registry.getHandler(ContractOutputType.IPv4);
    assertThat(handler.getSupportedContexts()).contains(ProcessingContext.FINDING);
    handler = registry.getHandler(ContractOutputType.IPv6);
    assertThat(handler.getSupportedContexts()).contains(ProcessingContext.FINDING);
    handler = registry.getHandler(ContractOutputType.Credentials);
    assertThat(handler.getSupportedContexts()).contains(ProcessingContext.FINDING);

    OutputProcessorHandler assetHandler = registry.getHandler(ContractOutputType.Asset);
    assertThat(assetHandler.getSupportedContexts()).contains(ProcessingContext.ASSET);

    OutputProcessorHandler cveHandler = registry.getHandler(ContractOutputType.CVE);
    assertThat(cveHandler.getSupportedContexts())
        .contains(ProcessingContext.FINDING, ProcessingContext.EXPECTATION);
  }
}
