package io.openaev.rest.inject.service;

import static io.openaev.utils.ExecutionTraceUtils.convertExecutionAction;

import io.openaev.database.model.*;
import io.openaev.rest.inject.form.InjectExecutionInput;
import jakarta.annotation.Nullable;
import java.util.Map;

/**
 * Context object for processing an inject execution.
 *
 * <p>Holds references to the inject, agent, input, and targeted assets map. Provides utility
 * methods to determine execution status and type.
 */
public record ExecutionProcessingContext(
    Inject inject,
    @Nullable Agent agent,
    InjectExecutionInput input,
    Map<String, Endpoint> valueTargetedAssetsMap) {

  /** Returns true if the execution status is SUCCESS. */
  public boolean isSuccess() {
    return ExecutionTraceStatus.SUCCESS.toString().equals(input.getStatus());
  }

  /** Returns true if the execution is for an injector (not agent). */
  public boolean isInjectorExecution() {
    return !isAgentExecution();
  }

  /** Returns true if the execution is for an agent. */
  public boolean isAgentExecution() {
    return agent != null;
  }

  /** Returns the execution action for this context. */
  public ExecutionTraceAction getAction() {
    return convertExecutionAction(input.getAction());
  }
}
