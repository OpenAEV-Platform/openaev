package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.Domain;
import io.openaev.database.repository.DomainRepository;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DomainComposer extends ComposerBase<Domain> {

  @Autowired private DomainRepository domainRepository;

  public class Composer extends InnerComposerBase<Domain> {
    private Domain domain;

    public Composer(Domain domain) {
      this.domain = domain;
    }

    @Override
    public Composer persist() {
      this.domain = domainRepository.save(domain);
      return this;
    }

    @Override
    public Domain get() {
      return domain;
    }

    public Set<Domain> getSet() {
      return Set.of(domain);
    }

    @Override
    public DomainComposer.Composer delete() {
      domainRepository.delete(this.domain);
      return this;
    }
  }

  public Composer forDomain(Domain domain) {
    generatedItems.add(domain);
    return new Composer(domain);
  }
}
