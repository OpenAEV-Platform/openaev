package io.openaev.utils.fixtures.platform;

import io.openaev.database.model.Group;
import io.openaev.database.repository.GroupRepository;
import io.openaev.utils.fixtures.composers.ComposerBase;
import io.openaev.utils.fixtures.composers.InnerComposerBase;
import io.openaev.utils.fixtures.composers.PlatformRoleComposer;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PlatformGroupComposer extends ComposerBase<Group> {

  @Autowired private GroupRepository groupRepository;

  public class Composer extends InnerComposerBase<Group> {

    private final Group group;
    private final List<PlatformRoleComposer.Composer> roleComposers = new ArrayList<>();

    public Composer(Group group) {
      this.group = group;
    }

    public PlatformGroupComposer.Composer withRole(PlatformRoleComposer.Composer roleComposer) {
      this.roleComposers.add(roleComposer);
      this.group.getRoles().add(roleComposer.get());
      return this;
    }

    @Override
    public PlatformGroupComposer.Composer persist() {
      roleComposers.forEach(PlatformRoleComposer.Composer::persist);
      groupRepository.save(group);
      return this;
    }

    @Override
    public PlatformGroupComposer.Composer delete() {
      group.getUsers().forEach(user -> user.getUnscopedGroups().remove(group));
      groupRepository.delete(group);
      return null;
    }

    @Override
    public Group get() {
      return this.group;
    }
  }

  public PlatformGroupComposer.Composer forPlatformGroup(Group group) {
    generatedItems.add(group);
    return new PlatformGroupComposer.Composer(group);
  }
}
