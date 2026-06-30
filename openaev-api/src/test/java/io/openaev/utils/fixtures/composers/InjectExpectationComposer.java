package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.TableTopInjectExpectation;
import io.openaev.database.model.TechnicalInjectExpectation;
import io.openaev.database.repository.InjectExpectationRepository;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class InjectExpectationComposer extends ComposerBase<BaseInjectExpectation> {
  @Autowired private InjectExpectationRepository injectExpectationRepository;

  public class Composer extends InnerComposerBase<BaseInjectExpectation> {
    private final BaseInjectExpectation baseInjectExpectation;
    private Optional<AssetGroupComposer.Composer> assetGroupComposer = Optional.empty();
    private Optional<TeamComposer.Composer> teamComposer = Optional.empty();
    private Optional<UserComposer.Composer> userComposer = Optional.empty();
    private Optional<EndpointComposer.Composer> endpointComposer = Optional.empty();
    private Optional<AgentComposer.Composer> agentComposer = Optional.empty();

    public Composer(BaseInjectExpectation baseInjectExpectation) {
      this.baseInjectExpectation = baseInjectExpectation;
    }

    public Composer withTeam(TeamComposer.Composer teamComposer) {
      this.teamComposer = Optional.of(teamComposer);
      asTableTop().setTeam(teamComposer.get());
      return this;
    }

    public Composer withUser(UserComposer.Composer userComposer) {
      this.userComposer = Optional.of(userComposer);
      asTableTop().setUser(userComposer.get());
      return this;
    }

    public Composer withAssetGroup(AssetGroupComposer.Composer assetGroupComposer) {
      this.assetGroupComposer = Optional.of(assetGroupComposer);
      asTechnical().setAssetGroup(assetGroupComposer.get());
      return this;
    }

    public Composer withEndpoint(EndpointComposer.Composer endpointComposer) {
      this.endpointComposer = Optional.of(endpointComposer);
      asTechnical().setAsset(endpointComposer.get());
      return this;
    }

    public Composer withAgent(AgentComposer.Composer agentComposer) {
      this.agentComposer = Optional.of(agentComposer);
      TechnicalInjectExpectation technical = asTechnical();
      technical.setAgent(agentComposer.get());
      technical.setAsset(agentComposer.get().getAsset());
      return this;
    }

    private TableTopInjectExpectation asTableTop() {
      if (baseInjectExpectation instanceof TableTopInjectExpectation tableTop) {
        return tableTop;
      }
      throw new IllegalStateException(
          "Expected a TableTopInjectExpectation (ARTICLE, CHALLENGE, MANUAL) but got "
              + baseInjectExpectation.getClass().getSimpleName());
    }

    private TechnicalInjectExpectation asTechnical() {
      if (baseInjectExpectation instanceof TechnicalInjectExpectation technical) {
        return technical;
      }
      throw new IllegalStateException(
          "Expected a TechnicalInjectExpectation (DETECTION, PREVENTION, VULNERABILITY) but got "
              + baseInjectExpectation.getClass().getSimpleName());
    }

    @Override
    public Composer persist() {
      assetGroupComposer.ifPresent(AssetGroupComposer.Composer::persist);
      endpointComposer.ifPresent(EndpointComposer.Composer::persist);
      agentComposer.ifPresent(AgentComposer.Composer::persist);
      teamComposer.ifPresent(TeamComposer.Composer::persist);
      userComposer.ifPresent(UserComposer.Composer::persist);
      injectExpectationRepository.save(baseInjectExpectation);
      return this;
    }

    @Override
    public InnerComposerBase<BaseInjectExpectation> delete() {
      assetGroupComposer.ifPresent(AssetGroupComposer.Composer::delete);
      endpointComposer.ifPresent(EndpointComposer.Composer::delete);
      agentComposer.ifPresent(AgentComposer.Composer::delete);
      teamComposer.ifPresent(TeamComposer.Composer::delete);
      userComposer.ifPresent(UserComposer.Composer::delete);
      injectExpectationRepository.delete(baseInjectExpectation);
      return this;
    }

    @Override
    public BaseInjectExpectation get() {
      return this.baseInjectExpectation;
    }
  }

  public Composer forExpectation(BaseInjectExpectation baseInjectExpectation) {
    generatedItems.add(baseInjectExpectation);
    return new Composer(baseInjectExpectation);
  }
}
