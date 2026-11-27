package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.FindingLocation;
import io.openaev.database.repository.FindingLocationRepository;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FindingLocationComposer extends ComposerBase<FindingLocation> {

  @Autowired private FindingLocationRepository findingLocationRepository;

  public class Composer extends InnerComposerBase<FindingLocation> {

    private final FindingLocation findingLocation;
    private Optional<EndpointComposer.Composer> endpointComposer = Optional.empty();
    private Optional<UserComposer.Composer> userComposer = Optional.empty();

    public Composer(FindingLocation findingLocation) {
      this.findingLocation = findingLocation;
    }

    public Composer withEndpoint(EndpointComposer.Composer endpointComposer) {
      this.endpointComposer = Optional.of(endpointComposer);
      this.findingLocation.setAsset(endpointComposer.get());
      return this;
    }

    public Composer withUser(UserComposer.Composer userComposer) {
      this.userComposer = Optional.of(userComposer);
      this.findingLocation.setUser(userComposer.get());
      return this;
    }

    @Override
    public FindingLocationComposer.Composer persist() {
      endpointComposer.ifPresent(EndpointComposer.Composer::persist);
      userComposer.ifPresent(UserComposer.Composer::persist);
      findingLocationRepository.save(this.findingLocation);
      return this;
    }

    @Override
    public FindingLocationComposer.Composer delete() {
      endpointComposer.ifPresent(EndpointComposer.Composer::delete);
      userComposer.ifPresent(UserComposer.Composer::delete);
      findingLocationRepository.delete(this.findingLocation);
      return this;
    }

    @Override
    public FindingLocation get() {
      return this.findingLocation;
    }
  }

  public FindingLocationComposer.Composer forFindingLocation(FindingLocation findingLocation) {
    generatedItems.add(findingLocation);
    return new FindingLocationComposer.Composer(findingLocation);
  }
}
