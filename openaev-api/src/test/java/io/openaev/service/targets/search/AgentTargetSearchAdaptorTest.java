package io.openaev.service.targets.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.openaev.database.model.Agent;
import io.openaev.database.model.Asset;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectTarget;
import io.openaev.database.repository.AgentRepository;
import io.openaev.service.targets.search.specifications.SearchSpecificationUtils;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("Agent target search adaptor")
class AgentTargetSearchAdaptorTest {

  @Mock private AgentRepository agentRepository;
  @Mock private SearchSpecificationUtils<Agent> specificationUtils;
  @Mock private HelperTargetSearchAdaptor helperTargetSearchAdaptor;

  @Test
  @DisplayName("given_agentWithoutExecutor_should_convertTargetWithoutThrowing")
  void given_agentWithoutExecutor_should_convertTargetWithoutThrowing() {
    // Arrange
    AgentTargetSearchAdaptor adaptor =
        new AgentTargetSearchAdaptor(
            agentRepository, specificationUtils, helperTargetSearchAdaptor);

    Agent agent = new Agent();
    agent.setId("agent-1");
    agent.setExecutedByUser("operator");
    Asset asset = new Asset();
    asset.setId("asset-1");
    agent.setAsset(asset);
    agent.setExecutor(null);

    when(helperTargetSearchAdaptor.buildTargetWithExpectations(any(), any(), eq(true)))
        .thenAnswer(invocation -> ((Supplier<InjectTarget>) invocation.getArgument(1)).get());

    // Act
    InjectTarget target =
        ReflectionTestUtils.invokeMethod(adaptor, "convertFromAgent", agent, new Inject());
    String targetSubtype = ReflectionTestUtils.invokeMethod(target, "getTargetSubtype");

    // Assert
    assertThat(target).isNotNull();
    assertThat(target.getId()).isEqualTo("agent-1");
    assertThat(targetSubtype).isEqualTo("Unknown");
  }
}
