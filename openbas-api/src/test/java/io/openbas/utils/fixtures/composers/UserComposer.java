package io.openbas.utils.fixtures.composers;

import io.openbas.database.model.Tag;
import io.openbas.database.model.User;
import io.openbas.database.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class UserComposer extends ComposerBase<User> {
  @Autowired UserRepository userRepository;

  public class Composer extends InnerComposerBase<User> {
    private final User user;
    private OrganizationComposer.Composer organizationComposer;
    private final List<TagComposer.Composer> tagComposers = new ArrayList<>();

    public Composer(User user) {
      this.user = user;
    }

    public Composer withOrganization(OrganizationComposer.Composer organizationComposer) {
      this.organizationComposer = organizationComposer;
      this.user.setOrganization(organizationComposer.get());
      return this;
    }

    public Composer withTag(TagComposer.Composer tagComposer) {
      tagComposers.add(tagComposer);
      Set<Tag> tempTags = this.user.getTags();
      tempTags.add(tagComposer.get());
      this.user.setTags(tempTags);
      return this;
    }

    public Composer withId(String id) {
      this.user.setId(id);
      return this;
    }

    @Override
    public Composer persist() {
      this.tagComposers.forEach(TagComposer.Composer::persist);
      if (organizationComposer != null) {
        organizationComposer.persist();
      }
      userRepository.save(user);
      return this;
    }

    @Override
    public Composer delete() {
      userRepository.delete(user);
      if (organizationComposer != null) {
        organizationComposer.delete();
      }
      this.tagComposers.forEach(TagComposer.Composer::delete);
      return this;
    }

    @Override
    public User get() {
      return this.user;
    }
  }

  public Composer forUser(User user) {
    generatedItems.add(user);
    return new Composer(user);
  }
}
