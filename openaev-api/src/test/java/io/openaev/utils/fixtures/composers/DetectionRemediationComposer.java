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
    private Optional<CollectorComposer.Composer> collectorComposer = Optional.empty();

    public Composer(DetectionRemediation detectionRemediation) {
      this.detectionRemediation = detectionRemediation;
    }

    public Composer withCollector(CollectorComposer.Composer newCollector) {
      collectorComposer = Optional.of(newCollector);
      detectionRemediation.setCollector(newCollector.get());
      return this;
    }

    @Override
    public Composer persist() {
      collectorComposer.ifPresent(CollectorComposer.Composer::persist);
      detectionRemediationRepository.save(this.detectionRemediation);
      return this;
    }

    @Override
    public Composer delete() {
      collectorComposer.ifPresent(CollectorComposer.Composer::delete);
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
