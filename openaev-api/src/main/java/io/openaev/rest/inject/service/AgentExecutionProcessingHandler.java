package io.openaev.rest.inject.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.ContractOutputElement;
import io.openaev.database.model.ExecutionTraceAction;
import io.openaev.database.model.OutputParser;
import io.openaev.output_processor.OutputProcessor;
import io.openaev.output_processor.OutputProcessorFactory;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class AgentExecutionProcessingHandler implements ExecutionProcessingHandler {

  private final StructuredOutputUtils structuredOutputUtils;
  private final OutputProcessorFactory outputProcessorFactory;

  /**
   * Determines if this handler supports the given execution context (agent execution).
   *
   * @param executionContext the execution context to check
   * @return true if the context is for an agent execution, false otherwise
   */
  @Override
  public boolean supports(ExecutionProcessingContext executionContext) {
    return executionContext.isAgentExecution();
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
              // Process findings for each compatible output parser
              getAllIsFindingCompatibleContractOutputs(outputParsers).stream()
                  .map(ContractOutputContext::from)
                  .forEach(
                      contractOutputCtx -> {
                        OutputProcessor processor =
                            outputProcessorFactory.getProcessor(contractOutputCtx.type());
                        JsonNode node = structuredOutput.path(contractOutputCtx.key());
                        if (!node.isMissingNode()) {
                          processor.process(executionContext, contractOutputCtx, node);
                        }
                      });
              return structuredOutput;
            });
  }

  /**
   * Retrieves all contract output elements from the output parsers that are compatible with
   * findings.
   *
   * @param outputParsers the set of output parsers to inspect
   * @return list of finding-compatible contract output elements
   */
  private List<ContractOutputElement> getAllIsFindingCompatibleContractOutputs(
      Set<OutputParser> outputParsers) {
    return outputParsers.stream()
        .flatMap(outputParser -> outputParser.getContractOutputElements().stream())
        .filter(ContractOutputElement::isFinding)
        .toList();
  }
}
