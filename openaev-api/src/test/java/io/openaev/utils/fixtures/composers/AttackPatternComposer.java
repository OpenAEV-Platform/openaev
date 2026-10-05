package io.openaev.utils.fixtures.composers;

import io.openaev.context.TenantContext;
import io.openaev.database.model.AttackPattern;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.AttackPatternRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AttackPatternComposer extends ComposerBase<AttackPattern> {
  @Autowired private AttackPatternRepository attackPatternRepository;

  public class Composer extends InnerComposerBase<AttackPattern> {
    private final AttackPattern attackPattern;

    public Composer(AttackPattern attackPattern) {
      this.attackPattern = attackPattern;
    }

    public Composer withId(String id) {
      this.attackPattern.setId(id);
      return this;
    }

    @Override
    public AttackPatternComposer.Composer persist() {
      // The listener now fails fast on an unattributed write; stamp the ambient tenant here when
      // the caller left it unset, mirroring EndpointComposer and SecurityPlatformComposer.
      if (attackPattern.getTenant() == null) {
        attackPattern.setTenant(new Tenant(TenantContext.getCurrentTenant()));
      }
      attackPatternRepository.save(attackPattern);
      return this;
    }

    @Override
    public AttackPatternComposer.Composer delete() {
      attackPatternRepository.delete(attackPattern);
      return this;
    }

    @Override
    public AttackPattern get() {
      return this.attackPattern;
    }
  }

  public AttackPatternComposer.Composer forAttackPattern(AttackPattern attackPattern) {
    generatedItems.add(attackPattern);
    return new AttackPatternComposer.Composer(attackPattern);
  }
}
