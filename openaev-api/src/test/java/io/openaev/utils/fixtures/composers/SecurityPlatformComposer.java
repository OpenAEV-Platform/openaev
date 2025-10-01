package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.SecurityPlatform;
import io.openaev.database.repository.SecurityPlatformRepository;
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
      securityPlatformRepository.save(this.securityPlatform);
      return this;
    }

    @Override
    public Composer delete() {
      securityPlatformRepository.delete(this.securityPlatform);
      return this;
    }

    @Override
    public SecurityPlatform get() {
      return this.securityPlatform;
    }
  }

  public Composer forSecurityPlatform(SecurityPlatform securityPlatform) {
    this.generatedItems.add(securityPlatform);
    return new Composer(securityPlatform);
  }
}
