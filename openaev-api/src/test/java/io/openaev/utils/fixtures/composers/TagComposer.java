package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.Tag;
import io.openaev.database.repository.TagRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TagComposer extends ComposerBase<Tag> {
  @Autowired private TagRepository tagRepository;

  public class Composer extends InnerComposerBase<Tag> {
    private final Tag tag;

    public Composer(Tag tag) {
      this.tag = tag;
    }

    public Composer withId(String id) {
      this.tag.setId(id);
      return this;
    }

    @Override
    public Composer persist() {
      tagRepository.save(tag);
      return this;
    }

    @Override
    public Composer delete() {
      tagRepository.delete(tag);
      return this;
    }

    @Override
    public Tag get() {
      return this.tag;
    }
  }

  public Composer forTag(Tag tag) {
    generatedItems.add(tag);
    return new Composer(tag);
  }
}
