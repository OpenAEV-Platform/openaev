package io.openaev.utils.fixtures.platform;

import io.openaev.database.model.PlatformGroup;
import io.openaev.database.repository.PlatformGroupRepository;
import io.openaev.utils.fixtures.composers.ComposerBase;
import io.openaev.utils.fixtures.composers.InnerComposerBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PlatformGroupComposer extends ComposerBase<PlatformGroup> {

  @Autowired private PlatformGroupRepository platformGroupRepository;

  public class Composer extends InnerComposerBase<PlatformGroup> {
    private final PlatformGroup platformGroup;

    public Composer(PlatformGroup platformGroup) {
      this.platformGroup = platformGroup;
    }

    @Override
    public PlatformGroupComposer.Composer persist() {
      platformGroupRepository.save(platformGroup);
      return this;
    }

    @Override
    public PlatformGroupComposer.Composer delete() {
      platformGroupRepository.delete(platformGroup);
      return null;
    }

    @Override
    public PlatformGroup get() {
      return this.platformGroup;
    }
  }

  public PlatformGroupComposer.Composer forPlatformGroup(PlatformGroup platformGroup) {
    generatedItems.add(platformGroup);
    return new PlatformGroupComposer.Composer(platformGroup);
  }
}
