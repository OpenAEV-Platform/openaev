package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.DetectionRemediation;
import io.openaev.database.repository.DetectionRemediationRepository;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DetectionRemediationComposer extends ComposerBase<DetectionRemediation> {

  @Autowired private DetectionRemediationRepository detectionRemediationRepository;

  public class Composer extends InnerComposerBase<DetectionRemediation> {

    private final DetectionRemediation detectionRemediation;
    private Optional<SecurityPlatformComposer.Composer> securityPlatformComposer = Optional.empty();

    public Composer(DetectionRemediation detectionRemediation) {
      this.detectionRemediation = detectionRemediation;
    }

    public Composer withSecurityPlatform(SecurityPlatformComposer.Composer newSecurityPlatform) {
      securityPlatformComposer = Optional.of(newSecurityPlatform);
      detectionRemediation.setSecurityPlatform(newSecurityPlatform.get());
      return this;
    }

    public void persistSecurityPlatformDependency() {
      securityPlatformComposer.ifPresent(
          spc -> {
            spc.persist();
            detectionRemediation.setSecurityPlatform(spc.get());
          });
    }

    @Override
    public Composer persist() {
      persistSecurityPlatformDependency();
      detectionRemediationRepository.save(this.detectionRemediation);
      return this;
    }

    @Override
    public Composer delete() {
      detectionRemediationRepository.delete(this.detectionRemediation);
      return this;
    }

    @Override
    public DetectionRemediation get() {
      return this.detectionRemediation;
    }
  }

  public DetectionRemediationComposer.Composer forDetectionRemediation(
      DetectionRemediation detectionRemediation) {
    generatedItems.add(detectionRemediation);
    return new DetectionRemediationComposer.Composer(detectionRemediation);
  }
}
