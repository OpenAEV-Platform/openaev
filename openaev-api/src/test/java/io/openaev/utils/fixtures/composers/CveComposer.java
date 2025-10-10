package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.Vulnerability;
import io.openaev.database.repository.CveRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CveComposer extends ComposerBase<Vulnerability> {

  @Autowired private CveRepository cveRepository;

  public class Composer extends InnerComposerBase<Vulnerability> {

    private final Vulnerability vulnerability;

    public Composer(Vulnerability vulnerability) {
      this.vulnerability = vulnerability;
    }

    @Override
    public CveComposer.Composer persist() {
      cveRepository.save(this.vulnerability);
      return this;
    }

    @Override
    public CveComposer.Composer delete() {
      cveRepository.delete(this.vulnerability);
      return this;
    }

    @Override
    public Vulnerability get() {
      return this.vulnerability;
    }
  }

  public CveComposer.Composer forCve(Vulnerability vulnerability) {
    generatedItems.add(vulnerability);
    return new CveComposer.Composer(vulnerability);
  }
}
