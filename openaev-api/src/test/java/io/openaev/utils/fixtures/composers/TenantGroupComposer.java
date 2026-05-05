package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.Group;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.GroupRepository;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TenantGroupComposer extends ComposerBase<Group> {

  @Autowired private GroupRepository groupRepository;
  @Autowired private EntityManager entityManager;

  public class Composer extends InnerComposerBase<Group> {

    private final Group group;
    private final List<TenantRoleComposer.Composer> roleComposers = new ArrayList<>();
    private final List<GrantComposer.Composer> grantComposers = new ArrayList<>();

    public Composer(Group group) {
      this.group = group;
    }

    public TenantGroupComposer.Composer withRole(TenantRoleComposer.Composer roleComposer) {
      this.roleComposers.add(roleComposer);
      this.group.getRoles().add(roleComposer.get());
      return this;
    }

    public TenantGroupComposer.Composer withGrant(GrantComposer.Composer grantComposer) {
      this.grantComposers.add(grantComposer);
      grantComposer.get().setGroup(group);
      this.group.getGrants().add(grantComposer.get());
      return this;
    }

    @Override
    public TenantGroupComposer.Composer persist() {
      group.setTenant(
          entityManager.getReference(
              Tenant.class, io.openaev.context.TenantContext.getCurrentTenant()));
      roleComposers.forEach(TenantRoleComposer.Composer::persist);
      groupRepository.save(group);
      grantComposers.forEach(GrantComposer.Composer::persist);
      return this;
    }

    @Override
    public TenantGroupComposer.Composer delete() {
      grantComposers.forEach(GrantComposer.Composer::delete);
      groupRepository.delete(group);
      roleComposers.forEach(TenantRoleComposer.Composer::delete);
      return null;
    }

    @Override
    public Group get() {
      return group;
    }
  }

  public TenantGroupComposer.Composer forGroup(Group group) {
    generatedItems.add(group);
    return new TenantGroupComposer.Composer(group);
  }
}
