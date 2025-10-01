package io.openaev.rest.tag;

import static io.openaev.helper.StreamHelper.iterableToSet;
import static io.openaev.utils.StringUtils.generateRandomColor;
import static java.time.Instant.now;

import io.openaev.database.model.Tag;
import io.openaev.database.repository.TagRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.tag.form.TagCreateInput;
import io.openaev.rest.tag.form.TagUpdateInput;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class TagService {

  private final TagRepository tagRepository;

  // -- CRUD --

  public Set<Tag> tagSet(@NotNull final List<String> tagIds) {
    return iterableToSet(this.tagRepository.findAllById(tagIds));
  }

  public Tag upsertTag(TagCreateInput input) {
    Optional<Tag> tag = tagRepository.findByName(input.getName());
    if (tag.isPresent()) {
      return tag.get();
    } else {
      Tag newTag = new Tag();
      newTag.setUpdateAttributes(input);
      return tagRepository.save(newTag);
    }
  }

  public Tag updateTag(String tagId, TagUpdateInput input) {
    Tag tag = tagRepository.findById(tagId).orElseThrow(ElementNotFoundException::new);
    tag.setUpdateAttributes(input);
    tag.setUpdatedAt(now());
    return tagRepository.save(tag);
  }

  /**
   * Generate a set of tag from a set of labels
   *
   * @param labels
   * @return set of tags
   */
  public Set<Tag> fetchTagsFromLabels(Set<String> labels) {
    Set<Tag> tags = new HashSet();

    if (labels != null) {
      for (String label : labels) {
        if (label == null || label.isBlank()) {
          continue;
        }
        TagCreateInput tagCreateInput = new TagCreateInput();
        tagCreateInput.setName(label);
        tagCreateInput.setColor(generateRandomColor());

        tags.add(upsertTag(tagCreateInput));
      }
    }

    return tags;
  }
}
