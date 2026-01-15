package io.openaev.rest.tag;

import static io.openaev.helper.StreamHelper.iterableToSet;
import static io.openaev.utils.StringUtils.generateRandomColor;
import static java.time.Instant.now;

import io.openaev.database.model.Tag;
import io.openaev.database.repository.TagRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.tag.form.TagCreateInput;
import io.openaev.rest.tag.form.TagUpdateInput;
import java.util.*;
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

  public Tag createTag(String name) {
    return createTag(name, getColourForName(name));
  }

  private Tag createTag(String name, String colour) {
    TagCreateInput tagCreateInput = new TagCreateInput();
    tagCreateInput.setName(name);
    tagCreateInput.setColor(colour);
    return upsertTag(tagCreateInput);
  }

  private String getColourForName(String name) {
    return Tag.WellKnown.getOrDefault(name, generateRandomColor());
  }

  public Tag upsertTag(TagCreateInput input) {
    Optional<Tag> tag = tagRepository.findByName(input.getName().toLowerCase());
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
   * Finds or creates tags based on a list of names. Created tags will be assigned a random colour.
   *
   * @param names collection of strings, each representing a requested tag
   * @return set of tags exactly matching the provided set of names
   */
  public Set<Tag> findOrCreateTagsFromNames(Set<String> names) {
    Set<Tag> tags = new HashSet<>();

    if (names != null) {
      for (String label : names) {
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

  /**
   * Ensures a collection of well known tags is created.
   *
   * @return the complete set of well known tags
   */
  public Set<Tag> ensureWellKnownTags() {
    Set<Tag> wellKnownTags = new HashSet<>();
    for (Map.Entry<String, String> entry : Tag.WellKnown.entrySet()) {
      wellKnownTags.add(
          this.tagRepository
              .findByName(entry.getKey())
              .orElseGet(
                  () -> {
                    Tag tag = new Tag();
                    tag.setName(entry.getKey());
                    tag.setColor(entry.getValue());
                    return tagRepository.save(tag);
                  }));
    }
    return wellKnownTags;
  }
}
