package io.openaev.utils.helpers;

import io.openaev.database.model.Inject;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectStatusFixture;
import io.openaev.utils.fixtures.composers.AgentComposer;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectStatusComposer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class InjectTestHelper {

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Inject getPendingInjectWithAssets(
      InjectComposer injectComposer,
      EndpointComposer endpointComposer,
      AgentComposer agentComposer,
      InjectStatusComposer injectStatusComposer) {
    return injectComposer
        .forInject(InjectFixture.getDefaultInject())
        .withEndpoint(
            endpointComposer
                .forEndpoint(EndpointFixture.createEndpoint())
                .withAgent(agentComposer.forAgent(AgentFixture.createDefaultAgentService()))
                .withAgent(agentComposer.forAgent(AgentFixture.createDefaultAgentSession())))
        .withInjectStatus(
            injectStatusComposer.forInjectStatus(InjectStatusFixture.createPendingInjectStatus()))
        .persist()
        .get();
  }
}
