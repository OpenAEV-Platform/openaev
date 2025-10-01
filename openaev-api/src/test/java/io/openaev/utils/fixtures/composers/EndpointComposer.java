package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.Agent;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Tag;
import io.openaev.database.repository.EndpointRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EndpointComposer extends ComposerBase<Endpoint> {
  @Autowired private EndpointRepository endpointRepository;

  public class Composer extends InnerComposerBase<Endpoint> {
    private final Endpoint endpoint;
    private final List<AgentComposer.Composer> agentComposers = new ArrayList<>();
    private final List<TagComposer.Composer> tagComposers = new ArrayList<>();

    public Composer(Endpoint endpoint) {
      this.endpoint = endpoint;
    }

    public Composer withAgent(AgentComposer.Composer agentComposer) {
      agentComposers.add(agentComposer);
      List<Agent> agents = endpoint.getAgents();
      Agent newAgent = agentComposer.get();
      newAgent.setAsset(this.endpoint);
      agents.add(newAgent);
      this.endpoint.setAgents(agents);
      return this;
    }

    public Composer withTag(TagComposer.Composer tagComposer) {
      tagComposers.add(tagComposer);
      Set<Tag> tags = endpoint.getTags();
      tags.add(tagComposer.get());
      this.endpoint.setTags(tags);
      return this;
    }

    @Override
    public Composer persist() {
      endpointRepository.save(endpoint);
      agentComposers.forEach(AgentComposer.Composer::persist);
      tagComposers.forEach(TagComposer.Composer::persist);
      return this;
    }

    @Override
    public Composer delete() {
      tagComposers.forEach(TagComposer.Composer::delete);
      agentComposers.forEach(AgentComposer.Composer::delete);
      endpointRepository.delete(endpoint);
      return this;
    }

    @Override
    public Endpoint get() {
      return this.endpoint;
    }
  }

  public EndpointComposer.Composer forEndpoint(Endpoint endpoint) {
    generatedItems.add(endpoint);
    return new EndpointComposer.Composer(endpoint);
  }
}
