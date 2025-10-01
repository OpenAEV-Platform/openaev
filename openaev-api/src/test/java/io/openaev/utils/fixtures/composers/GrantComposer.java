package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.Grant;
import io.openaev.database.repository.GrantRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class GrantComposer extends ComposerBase<Grant> {

  @Autowired private GrantRepository grantRepository;

  public class Composer extends InnerComposerBase<Grant> {

    private final Grant grant;

    public Composer(Grant grant) {
      this.grant = grant;
    }

    @Override
    public GrantComposer.Composer persist() {
      grantRepository.save(this.grant);
      return this;
    }

    @Override
    public GrantComposer.Composer delete() {
      grantRepository.delete(this.grant);
      return this;
    }

    @Override
    public Grant get() {
      return this.grant;
    }
  }

  public GrantComposer.Composer forGrant(Grant grant) {
    generatedItems.add(grant);
    return new GrantComposer.Composer(grant);
  }
}
