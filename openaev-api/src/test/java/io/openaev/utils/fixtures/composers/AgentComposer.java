package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.Agent;
import io.openaev.database.repository.AgentRepository;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AgentComposer extends ComposerBase<Agent> {
  @Autowired private AgentRepository agentRepository;

  public class Composer extends InnerComposerBase<Agent> {
    private final Agent agent;
    private Optional<ExecutorComposer.Composer> executorComposer = Optional.empty();

    public Composer(Agent agent) {
      this.agent = agent;
    }

    public Composer withExecutor(ExecutorComposer.Composer executorComposer) {
      this.executorComposer = Optional.of(executorComposer);
      this.agent.setExecutor(executorComposer.get());
      return this;
    }

    @Override
    public AgentComposer.Composer persist() {
      executorComposer.ifPresent(ExecutorComposer.Composer::persist);
      agentRepository.save(agent);
      return this;
    }

    @Override
    public AgentComposer.Composer delete() {
      executorComposer.ifPresent(ExecutorComposer.Composer::delete);
      agentRepository.delete(agent);
      return this;
    }

    @Override
    public Agent get() {
      return this.agent;
    }
  }

  public AgentComposer.Composer forAgent(Agent agent) {
    generatedItems.add(agent);
    return new AgentComposer.Composer(agent);
  }
}
