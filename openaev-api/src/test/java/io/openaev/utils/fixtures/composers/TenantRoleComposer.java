package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.Role;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.RoleRepository;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TenantRoleComposer extends ComposerBase<Role> {

  @Autowired private RoleRepository roleRepository;
  @Autowired private EntityManager entityManager;

  public class Composer extends InnerComposerBase<Role> {

    private final Role role;

    public Composer(Role role) {
      this.role = role;
    }

    @Override
    public TenantRoleComposer.Composer persist() {
      role.setTenant(
          entityManager.getReference(
              Tenant.class, io.openaev.context.TenantContext.getCurrentTenant()));
      roleRepository.save(this.role);
      return this;
    }

    @Override
    public TenantRoleComposer.Composer delete() {
      roleRepository.delete(this.role);
      return this;
    }

    @Override
    public Role get() {
      return this.role;
    }
  }

  public TenantRoleComposer.Composer forRole(Role role) {
    generatedItems.add(role);
    return new TenantRoleComposer.Composer(role);
  }
}
