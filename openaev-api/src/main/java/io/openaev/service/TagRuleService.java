package io.openaev.service;

import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;

import com.cronutils.utils.VisibleForTesting;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.Tag;
import io.openaev.database.model.TagRule;
import io.openaev.database.repository.AssetGroupRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.database.repository.TagRuleRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.exception.ForbiddenException;
import io.openaev.rest.tag.TagService;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class TagRuleService {

  private final TagRuleRepository tagRuleRepository;
  private final TagRepository tagRepository;
  private final TagService tagService;
  private final AssetGroupRepository assetGroupRepository;

  public Optional<TagRule> findById(String id) {
    return tagRuleRepository.findById(id);
  }

  public Optional<TagRule> findByTagName(String name) {
    return tagRuleRepository.findTagRuleByTagName(name);
  }

  public List<TagRule> findAll() {
    return StreamSupport.stream(tagRuleRepository.findAll().spliterator(), false)
        .collect(Collectors.toList());
  }

  public TagRule createTagRule(@NotBlank final String tagName, final List<String> assetGroupIds) {
    return createTagRule(getTag(tagName), assetGroupIds, false);
  }

  public TagRule createTagRule(
      @NotBlank final String tagName,
      final List<String> assetGroupIds,
      final boolean allowCreatingReserved) {
    return createTagRule(getTag(tagName), assetGroupIds, allowCreatingReserved);
  }

  public TagRule createTagRule(
      @NotBlank final Tag tag,
      final List<String> assetGroupIds,
      final boolean allowCreatingReserved) {
    // we block creation of tag rules for reserved tags
    if (TagRule.RESERVED_TAG_NAMES.contains(tag.getName()) && !allowCreatingReserved) {
      throw new ForbiddenException(
          "Creating a rule for the reserved tag '%s' is not permitted.".formatted(tag.getName()));
    }

    // if the tag  or one of the asset group doesn't exist we exist throw a ElementNotFoundException
    TagRule tagRule = new TagRule();
    tagRule.setTag(tag);
    tagRule.setAssetGroups(getAssetGroups(assetGroupIds));
    return tagRuleRepository.save(tagRule);
  }

  public TagRule updateTagRule(
      @NotBlank final String tagRuleId, final String tagName, final List<String> assetGroupIds) {
    // verify that the tag rule exists
    TagRule tagRule =
        tagRuleRepository
            .findById(tagRuleId)
            .orElseThrow(
                () -> new ElementNotFoundException("TagRule not found with id: " + tagRuleId));

    Tag newTag = getTag(tagName);

    // if one of the asset groups doesn't exist throw a ResourceNotFoundException
    List<AssetGroup> assetGroups = getAssetGroups(assetGroupIds);

    return updateTagRule(tagRule, newTag, assetGroups);
  }

  public TagRule updateTagRule(
      @NotBlank final TagRule tagRule, final Tag newTag, final List<AssetGroup> assetGroups) {
    try {
      if (TagRule.RESERVED_TAG_NAMES.contains(newTag.getName()) && isTagChanged(tagRule, newTag)) {
        throw new ForbiddenException(
            "Cannot change target tag to reserved tag " + newTag.getName());
      }
      tagRule.setTag(newTag);
    } catch (UnsupportedOperationException e) {
      throw new ForbiddenException("Cannot change the tag for protected tag rule.", e);
    }
    tagRule.setAssetGroups(assetGroups);
    return tagRuleRepository.save(tagRule);
  }

  public TagRule updateAssetGroups(final TagRule tagRule, final List<AssetGroup> assetGroups) {
    tagRule.setAssetGroups(assetGroups);
    return this.tagRuleRepository.save(tagRule);
  }

  public TagRule addAssetGroup(final TagRule tagRule, AssetGroup assetGroup) {
    Set<AssetGroup> assetGroups = new HashSet<>(tagRule.getAssetGroups());
    assetGroups.add(assetGroup);
    return this.updateAssetGroups(tagRule, new ArrayList<>(assetGroups.stream().toList()));
  }

  public Page<TagRule> searchTagRule(SearchPaginationInput searchPaginationInput) {
    return buildPaginationJPA(tagRuleRepository::findAll, searchPaginationInput, TagRule.class);
  }

  private boolean isTagChanged(TagRule tagRule, Tag newTag) {
    return tagRule.getTag() != null && !tagRule.getTag().equals(newTag);
  }

  public void deleteTagRule(@NotBlank final String tagRuleId) {
    // verify that the TagRule exists
    TagRule tagRule =
        tagRuleRepository
            .findById(tagRuleId)
            .orElseThrow(
                () -> new ElementNotFoundException("TagRule not found with id: " + tagRuleId));
    // we block deletion of tagrule for the opencti tag
    if (tagRule.isProtected()) {
      throw new ForbiddenException(
          "Deletion of a rule of the tag " + tagRule.getTag().getName() + " is not allowed");
    }

    tagRuleRepository.deleteById(tagRuleId);
  }

  @VisibleForTesting
  protected Tag getTag(@NotBlank final String tagName) {
    // TODO: tag name normalization needs to be implemented in a reusable method
    return tagRepository
        .findByName(tagName.toLowerCase())
        .orElseThrow(() -> new ElementNotFoundException("Tag not found with name: " + tagName));
  }

  /**
   * Return the set of asset groups to add from a tag id list
   *
   * @param tagIds
   * @return set of asset groups to add by default
   */
  public List<AssetGroup> getAssetGroupsFromTagIds(@NotNull final List<String> tagIds) {
    return this.tagRuleRepository.findByTags(tagIds).stream()
        .flatMap(tagRule -> tagRule.getAssetGroups().stream())
        .toList();
  }

  /**
   * Apply the rule to add the default asset groups to the input asset groups during Injects
   * creation
   *
   * @param tagIds list of Asset Groups of the Inject before applying the rules
   * @param inputAssetGroups list of Asset Groups of the Inject before applying the rules
   * @return return the new list of Asset Groups
   */
  public List<AssetGroup> applyTagRuleToInjectCreation(
      List<String> tagIds, List<AssetGroup> inputAssetGroups) {

    List<AssetGroup> defaultAssetGroups = this.getAssetGroupsFromTagIds(tagIds);

    // remove duplicates
    Set<String> uniqueAssetGrousIds = new HashSet<>();
    return Stream.concat(inputAssetGroups.stream(), defaultAssetGroups.stream())
        .filter(assetGroup -> uniqueAssetGrousIds.add(assetGroup.getId()))
        .toList();
  }

  /**
   * This method will verify based on the current list of tags and the new list of tags if some tags
   * linked to TagRules have been added
   *
   * @param currentTags
   * @param newTags
   * @return
   */
  public boolean checkIfRulesApply(
      @NotNull final List<String> currentTags, @NotNull final List<String> newTags) {
    List<AssetGroup> assetGroupsToAdd =
        getAssetGroupsFromTagIds(
            newTags.stream().filter(tag -> !currentTags.contains(tag)).toList());
    return !assetGroupsToAdd.isEmpty();
  }

  @VisibleForTesting
  protected List<AssetGroup> getAssetGroups(final List<String> assetGroupIds) {
    return assetGroupIds == null
        ? new ArrayList<>()
        : assetGroupIds.stream()
            .map(
                id ->
                    assetGroupRepository
                        .findById(id)
                        .orElseThrow(
                            () ->
                                new ElementNotFoundException(
                                    "Asset Group not found with id: " + id)))
            .collect(Collectors.toList());
  }

  public Set<TagRule> ensurePresetRules() {
    Set<TagRule> tagRules = new HashSet<>();
    for (String tagName : TagRule.RESERVED_TAG_NAMES) {
      Tag tag = tagRepository.findByName(tagName).orElseGet(() -> tagService.createTag(tagName));
      tagRules.add(
          this.findByTagName(tag.getName())
              .orElseGet(() -> this.createTagRule(tag, new ArrayList<>(), true)));
    }
    return tagRules;
  }
}
