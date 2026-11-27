package io.openaev.execution;

/** No agent could run the inject: all inactive, agentless, or every executor call failed. */
public class ExecutionExecutorException extends UnexecutableInjectException {
  public ExecutionExecutorException(String message) {
    super(message);
  }
}
