package io.openaev.rest.tag;

import static io.openaev.database.specification.TagSpecification.byName;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.helper.StreamHelper.iterableToSet;
import static io.openaev.utils.StringUtils.generateRandomColor;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;
import static java.time.Instant.now;

import io.openaev.database.model.Tag;
import io.openaev.database.repository.TagRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.tag.form.TagCreateInput;
import io.openaev.rest.tag.form.TagUpdateInput;
import io.openaev.utils.FilterUtilsJpa;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class TagService {

  private final TagRepository tagRepository;

  // -- CREATE --

  public Tag createTag(TagCreateInput input) {
    Tag tag = new Tag();
    tag.setUpdateAttributes(input);
    return tagRepository.save(tag);
  }

  public Tag createTag(String name) {
    TagCreateInput tagCreateInput = new TagCreateInput();
    tagCreateInput.setName(name);
    tagCreateInput.setColor(Tag.WellKnown.getOrDefault(name, generateRandomColor()));
    return upsertTag(tagCreateInput);
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

  // -- READ --

  public Set<Tag> tagSet(@NotNull final List<String> tagIds) {
    return iterableToSet(this.tagRepository.findAllById(tagIds));
  }

  public Iterable<Tag> tags() {
    return tagRepository.findAll();
  }

  public Page<Tag> search(SearchPaginationInput searchPaginationInput) {
    return buildPaginationJPA(
        (Specification<Tag> specification, Pageable pageable) ->
            this.tagRepository.findAll(specification, pageable),
        searchPaginationInput,
        Tag.class);
  }

  // -- UPDATE --

  public Tag updateTag(String tagId, TagUpdateInput input) {
    Tag tag = tagRepository.findById(tagId).orElseThrow(ElementNotFoundException::new);
    tag.setUpdateAttributes(input);
    tag.setUpdatedAt(now());
    return tagRepository.save(tag);
  }

  // -- DELETE --

  public void deleteTag(String tagId) {
    tagRepository.deleteById(tagId);
  }

  // -- OPTIONS --

  public List<FilterUtilsJpa.Option> optionsByName(String searchText) {
    return fromIterable(
            this.tagRepository.findAll(byName(searchText), Sort.by(Sort.Direction.ASC, "name")))
        .stream()
        .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
        .toList();
  }

  public List<FilterUtilsJpa.Option> optionsById(List<String> ids) {
    return fromIterable(this.tagRepository.findAllById(ids)).stream()
        .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
        .toList();
  }
}
