package io.openaev.utils;

import static io.openaev.database.model.BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS;
import static io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE.DETECTION;
import static io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE.PREVENTION;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.database.model.Agent;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Inject;
import io.openaev.database.model.TechnicalInjectExpectation;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.AssetGroupFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.InjectExpectationFixture;
import io.openaev.utils.fixtures.InjectFixture;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Children of the parent expectations, indexed once per inject")
class ExpectationChildrenIndexTest {

  /** Counts the scans of an inject's expectations: the index iterates them once. */
  private static final class CountingExpectations extends ArrayList<BaseInjectExpectation> {
    private int scans;

    @Override
    public Iterator<BaseInjectExpectation> iterator() {
      scans += 1;
      return super.iterator();
    }
  }

  private final CountingExpectations expectations = new CountingExpectations();
  private final Inject inject = InjectFixture.getDefaultInject();

  @BeforeEach
  void setUp() {
    inject.setExpectations(expectations);
  }

  private static Endpoint createEndpoint() {
    Endpoint endpoint = EndpointFixture.createEndpoint();
    endpoint.setId(UUID.randomUUID().toString());
    Agent agent = AgentFixture.createDefaultAgentService();
    agent.setId(UUID.randomUUID().toString());
    agent.setAsset(endpoint);
    endpoint.setAgents(new ArrayList<>(List.of(agent)));
    return endpoint;
  }

  private TechnicalInjectExpectation add(
      BaseInjectExpectation.EXPECTATION_TYPE type,
      AssetGroup assetGroup,
      Endpoint asset,
      Agent agent) {
    TechnicalInjectExpectation expectation =
        (TechnicalInjectExpectation)
            InjectExpectationFixture.createExpectationWithTypeAndStatus(type, SUCCESS);
    expectation.setInject(inject);
    expectation.setAssetGroup(assetGroup);
    expectation.setAsset(asset);
    expectation.setAgent(agent);
    expectations.add(expectation);
    return expectation;
  }

  @Test
  @DisplayName("Resolves the same children as the score propagation, scanning the inject once")
  void given_parentExpectations_should_resolveTheirChildrenWithOneScanOfTheInject() {
    // Arrange
    AssetGroup assetGroup = AssetGroupFixture.createDefaultAssetGroup("Workstations");
    assetGroup.setId(UUID.randomUUID().toString());
    Endpoint first = createEndpoint();
    Endpoint second = createEndpoint();
    TechnicalInjectExpectation group = add(DETECTION, assetGroup, null, null);
    TechnicalInjectExpectation firstAsset = add(DETECTION, assetGroup, first, null);
    TechnicalInjectExpectation firstAgent =
        add(DETECTION, assetGroup, first, first.getAgents().getFirst());
    TechnicalInjectExpectation secondAsset = add(DETECTION, assetGroup, second, null);
    TechnicalInjectExpectation secondAgent =
        add(DETECTION, assetGroup, second, second.getAgents().getFirst());
    TechnicalInjectExpectation firstAssetPrevention = add(PREVENTION, assetGroup, first, null);
    TechnicalInjectExpectation firstAgentPrevention =
        add(PREVENTION, assetGroup, first, first.getAgents().getFirst());
    ExpectationChildrenIndex index = new ExpectationChildrenIndex();

    // Act
    List<TechnicalInjectExpectation> groupChildren = index.childrenOf(group);
    List<TechnicalInjectExpectation> firstAssetChildren = index.childrenOf(firstAsset);
    List<TechnicalInjectExpectation> secondAssetChildren = index.childrenOf(secondAsset);
    List<TechnicalInjectExpectation> preventionChildren = index.childrenOf(firstAssetPrevention);
    List<TechnicalInjectExpectation> agentChildren = index.childrenOf(firstAgent);
    int scansOfTheIndex = expectations.scans;

    // Assert
    assertThat(groupChildren)
        .containsExactly(firstAsset, secondAsset)
        .isEqualTo(ExpectationUtils.getExpectationsAssetsForAssetGroup(group));
    assertThat(firstAssetChildren)
        .containsExactly(firstAgent)
        .isEqualTo(ExpectationUtils.getAgentsExpectationsForAsset(firstAsset));
    assertThat(secondAssetChildren).containsExactly(secondAgent);
    assertThat(preventionChildren).containsExactly(firstAgentPrevention);
    assertThat(agentChildren).isEmpty();
    assertThat(scansOfTheIndex).isEqualTo(1);
  }

  @Test
  @DisplayName("Has no children for an expectation without an inject")
  void given_expectationWithoutInject_should_haveNoChildren() {
    // Arrange
    TechnicalInjectExpectation orphan =
        (TechnicalInjectExpectation)
            InjectExpectationFixture.createExpectationWithTypeAndStatus(DETECTION, SUCCESS);
    orphan.setAsset(createEndpoint());

    // Act
    List<TechnicalInjectExpectation> children = new ExpectationChildrenIndex().childrenOf(orphan);

    // Assert
    assertThat(children).isEmpty();
  }
}
