package io.openaev.utils.fixtures.platform;

import io.openaev.database.model.PlatformRole;
import io.openaev.database.repository.PlatformRoleRepository;
import io.openaev.utils.fixtures.composers.ComposerBase;
import io.openaev.utils.fixtures.composers.InnerComposerBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PlatformRoleComposer extends ComposerBase<PlatformRole> {

  @Autowired private PlatformRoleRepository platformRoleRepository;

  public class Composer extends InnerComposerBase<PlatformRole> {
    private final PlatformRole platformRole;

    public Composer(PlatformRole platformRole) {
      this.platformRole = platformRole;
    }

    @Override
    public PlatformRoleComposer.Composer persist() {
      platformRoleRepository.save(platformRole);
      return this;
    }

    @Override
    public PlatformRoleComposer.Composer delete() {
      platformRoleRepository.delete(platformRole);
      return null;
    }

    @Override
    public PlatformRole get() {
      return this.platformRole;
    }
  }

  public PlatformRoleComposer.Composer forPlatformRole(PlatformRole platformRole) {
    generatedItems.add(platformRole);
    return new PlatformRoleComposer.Composer(platformRole);
  }
}
