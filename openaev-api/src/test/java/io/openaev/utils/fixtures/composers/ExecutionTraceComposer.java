package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.ExecutionTrace;
import io.openaev.database.repository.ExecutionTraceRepository;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ExecutionTraceComposer extends ComposerBase<ExecutionTrace> {
  @Autowired private ExecutionTraceRepository executionTraceRepository;

  public class Composer extends InnerComposerBase<ExecutionTrace> {
    private final ExecutionTrace executionTrace;
    private Optional<AgentComposer.Composer> agentComposer = Optional.empty();

    public Composer(ExecutionTrace executionTrace) {
      this.executionTrace = executionTrace;
    }

    public Composer withAgent(AgentComposer.Composer agent) {
      agentComposer = Optional.of(agent);
      this.executionTrace.setAgent(agent.get());
      return this;
    }

    @Override
    public ExecutionTraceComposer.Composer persist() {
      executionTraceRepository.save(executionTrace);
      return this;
    }

    @Override
    public ExecutionTraceComposer.Composer delete() {
      agentComposer.ifPresent(AgentComposer.Composer::delete);
      executionTraceRepository.delete(executionTrace);
      return this;
    }

    @Override
    public ExecutionTrace get() {
      return this.executionTrace;
    }
  }

  public ExecutionTraceComposer.Composer forExecutionTrace(ExecutionTrace executionTrace) {
    generatedItems.add(executionTrace);
    return new ExecutionTraceComposer.Composer(executionTrace);
  }
}
