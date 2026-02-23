package io.openaev.utils.fixtures.tenants;

import io.openaev.database.model.Tenant;
import io.openaev.database.repository.TenantRepository;
import io.openaev.utils.fixtures.composers.ComposerBase;
import io.openaev.utils.fixtures.composers.InnerComposerBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TenantComposer extends ComposerBase<Tenant> {
  @Autowired private TenantRepository tenantRepository;

  public class Composer extends InnerComposerBase<Tenant> {
    private final Tenant tenant;

    public Composer(Tenant tenant) {
      this.tenant = tenant;
    }

    @Override
    public TenantComposer.Composer persist() {
      tenantRepository.save(tenant);
      return this;
    }

    @Override
    public TenantComposer.Composer delete() {
      tenantRepository.delete(tenant);
      return null;
    }

    @Override
    public Tenant get() {
      return this.tenant;
    }
  }

  public TenantComposer.Composer forTenant(Tenant tenant) {
    generatedItems.add(tenant);
    return new TenantComposer.Composer(tenant);
  }
}
