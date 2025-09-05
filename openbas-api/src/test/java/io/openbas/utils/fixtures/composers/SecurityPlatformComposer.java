package io.openbas.utils.fixtures.composers;

import io.openbas.database.model.SecurityPlatform;
import io.openbas.database.repository.SecurityPlatformRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SecurityPlatformComposer extends ComposerBase<SecurityPlatform> {
  @Autowired private SecurityPlatformRepository securityPlatformRepository;

  public class Composer extends InnerComposerBase<SecurityPlatform> {
    private final SecurityPlatform securityPlatform;

    public Composer(SecurityPlatform securityPlatform) {
      this.securityPlatform = securityPlatform;
    }

    @Override
    public Composer persist() {
      securityPlatformRepository.save(securityPlatform);
      return this;
    }

    @Override
    public Composer delete() {
      securityPlatformRepository.delete(securityPlatform);
      return this;
    }

    @Override
    public SecurityPlatform get() {
      return this.securityPlatform;
    }
  }

  public SecurityPlatformComposer.Composer forSecurityPlatform(SecurityPlatform securityPlatform) {
    generatedItems.add(securityPlatform);
    return new SecurityPlatformComposer.Composer(securityPlatform);
  }
}
