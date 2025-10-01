package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.Role;
import io.openaev.database.repository.RoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RoleComposer extends ComposerBase<Role> {

  @Autowired private RoleRepository roleRepository;

  public class Composer extends InnerComposerBase<Role> {

    private final Role role;

    public Composer(Role role) {
      this.role = role;
    }

    @Override
    public RoleComposer.Composer persist() {
      roleRepository.save(this.role);
      return this;
    }

    @Override
    public RoleComposer.Composer delete() {
      roleRepository.delete(this.role);
      return this;
    }

    @Override
    public Role get() {
      return this.role;
    }
  }

  public RoleComposer.Composer forRole(Role role) {
    generatedItems.add(role);
    return new RoleComposer.Composer(role);
  }
}
