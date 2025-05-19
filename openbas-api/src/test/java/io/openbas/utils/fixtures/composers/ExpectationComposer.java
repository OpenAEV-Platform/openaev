package io.openbas.utils.fixtures.composers;

import io.openbas.database.model.InjectExpectation;
import io.openbas.database.repository.InjectExpectationRepository;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ExpectationComposer extends ComposerBase<InjectExpectation> {
  @Autowired private InjectExpectationRepository injectExpectationRepository;

  public class Composer extends InnerComposerBase<InjectExpectation> {
    private final InjectExpectation injectExpectation;
    private Optional<AssetGroupComposer.Composer> assetGroupComposer = Optional.empty();
    private Optional<TeamComposer.Composer> teamComposer = Optional.empty();
    private Optional<UserComposer.Composer> userComposer = Optional.empty();
    private Optional<EndpointComposer.Composer> endpointComposer = Optional.empty();

    public Composer(InjectExpectation injectExpectation) {
      this.injectExpectation = injectExpectation;
    }

    public Composer withTeam(TeamComposer.Composer teamComposer) {
      this.teamComposer = Optional.of(teamComposer);
      this.injectExpectation.setTeam(teamComposer.get());
      return this;
    }

    public Composer withUser(UserComposer.Composer userComposer) {
      this.userComposer = Optional.of(userComposer);
      this.injectExpectation.setUser(userComposer.get());
      return this;
    }

    public Composer withAssetGroup(AssetGroupComposer.Composer assetGroupComposer) {
      this.assetGroupComposer = Optional.of(assetGroupComposer);
      this.injectExpectation.setAssetGroup(assetGroupComposer.get());
      return this;
    }

    public Composer withEndpoint(EndpointComposer.Composer endpointComposer) {
      this.endpointComposer = Optional.of(endpointComposer);
      this.injectExpectation.setAsset(endpointComposer.get());
      return this;
    }

    @Override
    public Composer persist() {
      assetGroupComposer.ifPresent(AssetGroupComposer.Composer::persist);
      endpointComposer.ifPresent(EndpointComposer.Composer::persist);
      teamComposer.ifPresent(TeamComposer.Composer::persist);
      userComposer.ifPresent(UserComposer.Composer::persist);
      injectExpectationRepository.save(injectExpectation);
      return this;
    }

    @Override
    public InnerComposerBase<InjectExpectation> delete() {
      assetGroupComposer.ifPresent(AssetGroupComposer.Composer::delete);
      endpointComposer.ifPresent(EndpointComposer.Composer::delete);
      teamComposer.ifPresent(TeamComposer.Composer::delete);
      userComposer.ifPresent(UserComposer.Composer::delete);
      injectExpectationRepository.delete(injectExpectation);
      return this;
    }

    @Override
    public InjectExpectation get() {
      return this.injectExpectation;
    }
  }

  public Composer forExpectation(InjectExpectation injectExpectation) {
    generatedItems.add(injectExpectation);
    return new Composer(injectExpectation);
  }
}
