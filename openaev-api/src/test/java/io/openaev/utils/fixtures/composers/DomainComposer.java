package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.Domain;
import io.openaev.database.repository.DomainRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DomainComposer extends ComposerBase<Domain> {

  public static final Domain TOCLASSIFY =
      new Domain(null, "To classify", "#000000", Instant.now(), null);

  @Autowired private DomainRepository domainRepository;

  public class Composer extends InnerComposerBase<Domain> {
    private Domain domain;

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
      Optional<Domain> domainOpt = domainRepository.findByName(domain.getName());
      if (domainOpt.isEmpty()) {
        this.domain = domainRepository.save(domain);
        return this;
      }

      this.domain = domainOpt.get();
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
    return new Composer(domain != null ? domain : new Domain());
  }

  public Composer forDefaultToClassifyDomain() {
    return new Composer(TOCLASSIFY);
  }
}
