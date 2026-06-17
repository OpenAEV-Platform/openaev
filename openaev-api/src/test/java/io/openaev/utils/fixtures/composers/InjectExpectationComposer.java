package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.repository.InjectExpectationRepository;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class InjectExpectationComposer extends ComposerBase<BaseInjectExpectation> {
  @Autowired private InjectExpectationRepository injectExpectationRepository;

  public class Composer extends InnerComposerBase<BaseInjectExpectation> {
    private final BaseInjectExpectation BaseInjectExpectation;
    private Optional<AssetGroupComposer.Composer> assetGroupComposer = Optional.empty();
    private Optional<TeamComposer.Composer> teamComposer = Optional.empty();
    private Optional<UserComposer.Composer> userComposer = Optional.empty();
    private Optional<EndpointComposer.Composer> endpointComposer = Optional.empty();
    private Optional<AgentComposer.Composer> agentComposer = Optional.empty();

    public Composer(BaseInjectExpectation BaseInjectExpectation) {
      this.BaseInjectExpectation = BaseInjectExpectation;
    }

    public Composer withTeam(TeamComposer.Composer teamComposer) {
      this.teamComposer = Optional.of(teamComposer);
      this.BaseInjectExpectation.setTeam(teamComposer.get());
      return this;
    }

    public Composer withUser(UserComposer.Composer userComposer) {
      this.userComposer = Optional.of(userComposer);
      this.BaseInjectExpectation.setUser(userComposer.get());
      return this;
    }

    public Composer withAssetGroup(AssetGroupComposer.Composer assetGroupComposer) {
      this.assetGroupComposer = Optional.of(assetGroupComposer);
      this.BaseInjectExpectation.setAssetGroup(assetGroupComposer.get());
      return this;
    }

    public Composer withEndpoint(EndpointComposer.Composer endpointComposer) {
      this.endpointComposer = Optional.of(endpointComposer);
      this.BaseInjectExpectation.setAsset(endpointComposer.get());
      return this;
    }

    public Composer withAgent(AgentComposer.Composer agentComposer) {
      this.agentComposer = Optional.of(agentComposer);
      this.BaseInjectExpectation.setAgent(agentComposer.get());
      this.BaseInjectExpectation.setAsset(agentComposer.get().getAsset());
      return this;
    }

    @Override
    public Composer persist() {
      assetGroupComposer.ifPresent(AssetGroupComposer.Composer::persist);
      endpointComposer.ifPresent(EndpointComposer.Composer::persist);
      agentComposer.ifPresent(AgentComposer.Composer::persist);
      teamComposer.ifPresent(TeamComposer.Composer::persist);
      userComposer.ifPresent(UserComposer.Composer::persist);
      injectExpectationRepository.save(BaseInjectExpectation);
      return this;
    }

    @Override
    public InnerComposerBase<BaseInjectExpectation> delete() {
      assetGroupComposer.ifPresent(AssetGroupComposer.Composer::delete);
      endpointComposer.ifPresent(EndpointComposer.Composer::delete);
      agentComposer.ifPresent(AgentComposer.Composer::delete);
      teamComposer.ifPresent(TeamComposer.Composer::delete);
      userComposer.ifPresent(UserComposer.Composer::delete);
      injectExpectationRepository.delete(BaseInjectExpectation);
      return this;
    }

    @Override
    public BaseInjectExpectation get() {
      return this.BaseInjectExpectation;
    }
  }

  public Composer forExpectation(BaseInjectExpectation BaseInjectExpectation) {
    generatedItems.add(BaseInjectExpectation);
    return new Composer(BaseInjectExpectation);
  }
}
