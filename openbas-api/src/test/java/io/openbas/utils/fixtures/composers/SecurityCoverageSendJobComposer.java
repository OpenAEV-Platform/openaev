package io.openbas.utils.fixtures.composers;

import io.openbas.database.model.SecurityCoverageSendJob;
import io.openbas.database.repository.SecurityCoverageSendJobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SecurityCoverageSendJobComposer extends ComposerBase<SecurityCoverageSendJob> {
  @Autowired private SecurityCoverageSendJobRepository securityCoverageSendJobRepository;

  public class Composer extends InnerComposerBase<SecurityCoverageSendJob> {
    private final SecurityCoverageSendJob securityCoverageSendJob;

    public Composer(SecurityCoverageSendJob securityCoverageSendJob) {
      this.securityCoverageSendJob = securityCoverageSendJob;
    }

    @Override
    public Composer persist() {
      securityCoverageSendJobRepository.save(this.securityCoverageSendJob);
      return this;
    }

    @Override
    public Composer delete() {
      securityCoverageSendJobRepository.delete(this.securityCoverageSendJob);
      return this;
    }

    @Override
    public SecurityCoverageSendJob get() {
      return this.securityCoverageSendJob;
    }
  }

  public Composer forSecurityCoverageSendJob(SecurityCoverageSendJob securityCoverageSendJob) {
    this.generatedItems.add(securityCoverageSendJob);
    return new Composer(securityCoverageSendJob);
  }
}
