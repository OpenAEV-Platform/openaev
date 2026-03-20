package io.openaev.rest.inject.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.ExecutionTraceAction;
import io.openaev.database.model.InjectorContract;
import io.openaev.injector_contract.outputs.InjectorContractContentOutputElement;
import io.openaev.output_processor.OutputProcessorFactory;
import io.openaev.rest.injector_contract.InjectorContractContentUtils;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Handler for processing inject executions triggered by an injector (not an agent).
 *
 * <p>This handler generates structured output from the raw execution input and processes additional
 * capabilities such as findings extraction, expectation matching, or asset creation if applicable.
 */
@Slf4j
@Component
public class InjectorExecutionProcessingHandler extends AbstractExecutionProcessingHandler {

  @Resource protected ObjectMapper mapper;
  private final InjectorContractContentUtils injectorContractContentUtils;

  public InjectorExecutionProcessingHandler(
      OutputProcessorFactory outputProcessorFactory,
      InjectorContractContentUtils injectorContractContentUtils) {
    super(outputProcessorFactory);
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
        || !ExecutionTraceAction.COMPLETE.equals(executionContext.getAction())) {
      return Optional.empty();
    }

    String outputStructured = executionContext.input().getOutputStructured();
    if (outputStructured == null || outputStructured.isBlank()) {
      log.debug("No structured output provided; skipping injector execution post-processing.");
      return Optional.empty();
    }

    ObjectNode structuredOutput;
    try {
      structuredOutput = mapper.readValue(outputStructured, ObjectNode.class);
    } catch (JsonProcessingException e) {
      log.warn(
          "Failed to parse structured output as JSON; skipping injector execution post-processing.",
          e);
      return Optional.empty();
    }

    if (structuredOutput == null || structuredOutput.isMissingNode()) {
      return Optional.empty();
    }

    InjectorContract injectorContract =
        executionContext.inject().getInjectorContract().orElseThrow();

    List<ContractOutputContext> contractOutputContexts =
        getAllContractOutputs(injectorContract).stream().map(ContractOutputContext::from).toList();
    dispatchToProcessors(executionContext, contractOutputContexts, structuredOutput);

    return Optional.of(structuredOutput);
  }

  /**
   * Retrieves all contract output elements from the injector contract.
   *
   * @param injectorContract the injector contract to inspect
   * @return list of contract output elements
   */
  private List<InjectorContractContentOutputElement> getAllContractOutputs(
      InjectorContract injectorContract) {
    return injectorContractContentUtils
        .getContractOutputs(injectorContract.getConvertedContent(), mapper)
        .stream()
        .toList();
  }
}
