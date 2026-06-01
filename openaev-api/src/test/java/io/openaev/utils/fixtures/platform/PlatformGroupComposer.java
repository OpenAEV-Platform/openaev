package io.openaev.utils.fixtures.platform;

import io.openaev.database.model.Group;
import io.openaev.database.repository.GroupRepository;
import io.openaev.utils.fixtures.composers.ComposerBase;
import io.openaev.utils.fixtures.composers.InnerComposerBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PlatformGroupComposer extends ComposerBase<Group> {

  @Autowired private GroupRepository groupRepository;

  public class Composer extends InnerComposerBase<Group> {

    private final Group group;

    public Composer(Group group) {
      this.group = group;
    }

    @Override
    public PlatformGroupComposer.Composer persist() {
      groupRepository.save(group);
      return this;
    }

    @Override
    public PlatformGroupComposer.Composer delete() {
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
