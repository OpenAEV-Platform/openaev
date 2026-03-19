package io.openaev.rest.inject.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.output_processor.OutputProcessorFactory;
import java.util.List;

public abstract class AbstractExecutionProcessingHandler implements ExecutionProcessingHandler {

  protected final OutputProcessorFactory outputProcessorFactory;

  protected AbstractExecutionProcessingHandler(OutputProcessorFactory outputProcessorFactory) {
    this.outputProcessorFactory = outputProcessorFactory;
  }

  /**
   * Dispatches each contract output context to its corresponding processor, if one exists, using
   * values extracted from the given structured output node.
   *
   * @param executionContext the current execution context
   * @param contractOutputContexts the list of contract output contexts to dispatch
   * @param structuredOutput the structured output node to read values from
   */
  protected void dispatchToProcessors(
      ExecutionProcessingContext executionContext,
      List<ContractOutputContext> contractOutputContexts,
      ObjectNode structuredOutput) {
    contractOutputContexts.forEach(
        contractOutputCtx ->
            outputProcessorFactory
                .getProcessor(contractOutputCtx.type())
                .ifPresent(
                    processor -> {
                      JsonNode node = structuredOutput.path(contractOutputCtx.key());
                      if (!node.isMissingNode()) {
                        processor.process(executionContext, contractOutputCtx, node);
                      }
                    }));
  }
}
