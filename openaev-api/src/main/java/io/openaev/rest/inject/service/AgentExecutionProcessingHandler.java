package io.openaev.rest.inject.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.ExecutionTraceAction;
import io.openaev.database.model.OutputParser;
import io.openaev.output_processor.OutputProcessorFactory;
import io.openaev.rest.injector_contract.InjectorContractContentUtils;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Handler for processing inject executions triggered by an agent.
 *
 * <p>This handler generates structured output from the raw execution input and processes additional
 * capabilities such as findings extraction, expectation matching, or asset creation if applicable.
 */
@Slf4j
@Component
public class AgentExecutionProcessingHandler extends AbstractExecutionProcessingHandler {

  private final StructuredOutputUtils structuredOutputUtils;
  private final InjectorContractContentUtils injectorContractContentUtils;

  public AgentExecutionProcessingHandler(
      OutputProcessorFactory outputProcessorFactory,
      StructuredOutputUtils structuredOutputUtils,
      InjectorContractContentUtils injectorContractContentUtils) {
    super(outputProcessorFactory);
    this.structuredOutputUtils = structuredOutputUtils;
    this.injectorContractContentUtils = injectorContractContentUtils;
  }

  /**
   * Processes the execution context, generating structured output and handling additional
   * capabilities such as findings extraction, expectation matching, or asset creation.
   *
   * @param executionContext the execution context to process
   * @return an optional ObjectNode result, if processing produces output
   * @throws JsonProcessingException if JSON serialization fails during processing
   */
  public Optional<ObjectNode> processContext(ExecutionProcessingContext executionContext)
      throws JsonProcessingException {
    if (!executionContext.isSuccess()
        || !ExecutionTraceAction.EXECUTION.equals(executionContext.getAction())) {
      return Optional.empty();
    }

    Set<OutputParser> outputParsers =
        structuredOutputUtils.extractOutputParsers(executionContext.inject());

    // Attempt to compute structured output from the raw message
    return structuredOutputUtils
        .computeStructuredOutputFromOutputParsers(
            outputParsers, executionContext.input().getMessage())
        .map(
            structuredOutput -> {
              List<ContractOutputContext> contractOutputContexts =
                  injectorContractContentUtils.getAllContractOutputs(outputParsers).stream()
                      .map(ContractOutputContext::from)
                      .toList();
              dispatchToProcessors(executionContext, contractOutputContexts, structuredOutput);
              return structuredOutput;
            });
  }
}
