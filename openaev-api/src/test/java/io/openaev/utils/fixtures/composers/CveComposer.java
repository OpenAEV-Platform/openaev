package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.Cve;
import io.openaev.database.repository.CveRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CveComposer extends ComposerBase<Cve> {

  @Autowired private CveRepository cveRepository;

  public class Composer extends InnerComposerBase<Cve> {

    private final Cve cve;

    public Composer(Cve cve) {
      this.cve = cve;
    }

    @Override
    public CveComposer.Composer persist() {
      cveRepository.save(this.cve);
      return this;
    }

    @Override
    public CveComposer.Composer delete() {
      cveRepository.delete(this.cve);
      return this;
    }

    @Override
    public Cve get() {
      return this.cve;
    }
  }

  public CveComposer.Composer forCve(Cve cve) {
    generatedItems.add(cve);
    return new CveComposer.Composer(cve);
  }
}
