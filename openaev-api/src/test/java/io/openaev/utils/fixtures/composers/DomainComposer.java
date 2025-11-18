package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.Domain;
import io.openaev.database.repository.DomainRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DomainComposer extends ComposerBase<Domain> {

  @Autowired private DomainRepository domainRepository;

  public class Composer extends InnerComposerBase<Domain> {
    private final Domain domain;

    public Composer(Domain domain) {
      this.domain = domain;
    }

    public Composer withId(String id) {
      this.domain.setId(id);
      return this;
    }

    public Composer withName(String name) {
      this.domain.setName(name);
      return this;
    }

    public Composer withColor(String color) {
      this.domain.setColor(color);
      return this;
    }

    @Override
    public Composer persist() {
      domainRepository.save(domain);
      return this;
    }

    @Override
    public Domain get() {
      return domain;
    }

    @Override
    public DomainComposer.Composer delete() {
      domainRepository.delete(this.domain);
      return this;
    }
  }

  public Composer forDomain(Domain domain) {
    return new Composer(domain != null ? domain : new Domain());
  }
}
