package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.Role;
import io.openaev.database.repository.RoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PlatformRoleComposer extends ComposerBase<Role> {

  @Autowired private RoleRepository roleRepository;

  public class Composer extends InnerComposerBase<Role> {
    private final Role role;

    public Composer(Role role) {
      this.role = role;
    }

    @Override
    public PlatformRoleComposer.Composer persist() {
      roleRepository.save(role);
      return this;
    }

    @Override
    public PlatformRoleComposer.Composer delete() {
      roleRepository.delete(role);
      return null;
    }

    @Override
    public Role get() {
      return this.role;
    }
  }

  public PlatformRoleComposer.Composer forPlatformRole(Role role) {
    generatedItems.add(role);
    return new PlatformRoleComposer.Composer(role);
  }
}
