package io.openbas.utils.fixtures.composers;

import io.openbas.database.model.SecurityCoverage;
import io.openbas.database.repository.SecurityCoverageRepository;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SecurityCoverageComposer extends ComposerBase<SecurityCoverage> {
  @Autowired private SecurityCoverageRepository securityCoverageRepository;

  public class Composer extends InnerComposerBase<SecurityCoverage> {
    private final SecurityCoverage securityCoverage;
    private Optional<ScenarioComposer.Composer> scenarioComposer = Optional.empty();

    public Composer(SecurityCoverage securityCoverage) {
      this.securityCoverage = securityCoverage;
    }

    public Composer withScenario(ScenarioComposer.Composer scenarioWrapper) {
      scenarioComposer = Optional.of(scenarioWrapper);
      this.securityCoverage.setScenario(scenarioWrapper.get());
      return this;
    }

    @Override
    public Composer persist() {
      scenarioComposer.ifPresent(ScenarioComposer.Composer::persist);
      securityCoverageRepository.save(this.securityCoverage);
      return this;
    }

    @Override
    public Composer delete() {
      scenarioComposer.ifPresent(ScenarioComposer.Composer::delete);
      securityCoverageRepository.delete(this.securityCoverage);
      return this;
    }

    @Override
    public SecurityCoverage get() {
      return this.securityCoverage;
    }
  }

  public Composer forSecurityCoverage(SecurityCoverage securityCoverage) {
    generatedItems.add(securityCoverage);
    return new Composer(securityCoverage);
  }
}
