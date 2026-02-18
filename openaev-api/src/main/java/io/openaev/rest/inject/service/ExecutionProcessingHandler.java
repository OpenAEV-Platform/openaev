package io.openaev.rest.inject.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Optional;

/**
 * Handler interface for processing inject execution contexts.
 *
 * <p>Implementations determine if they support a given context and process it, typically generating
 * structured output and then processing additional capabilities (findings, expectation matching,
 * asset creation).
 */
public interface ExecutionProcessingHandler {
  /**
   * Determines if this handler supports the given execution context.
   *
   * @param executionContext the execution context to check
   * @return true if supported, false otherwise
   */
  boolean supports(ExecutionProcessingContext executionContext);

  /**
   * Processes the execution context, generating structured output and handling additional
   * capabilities.
   *
   * @param executionContext the execution context to process
   * @return an optional ObjectNode result, if processing produces output
   * @throws JsonProcessingException if JSON serialization fails during processing
   */
  Optional<ObjectNode> processContext(ExecutionProcessingContext executionContext)
      throws JsonProcessingException;
}
