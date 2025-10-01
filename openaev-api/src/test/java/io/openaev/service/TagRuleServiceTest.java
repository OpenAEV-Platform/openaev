package io.openaev.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.IntegrationTest;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.Tag;
import io.openaev.database.model.TagRule;
import io.openaev.database.repository.AssetGroupRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.database.repository.TagRuleRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.exception.ForbiddenException;
import io.openaev.utils.fixtures.AssetGroupFixture;
import io.openaev.utils.fixtures.TagFixture;
import io.openaev.utils.fixtures.TagRuleFixture;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class TagRuleServiceTest extends IntegrationTest {
  private static final String TAG_RULE_ID = "tagruleid";
  private static final String TAG_RULE_ID_2 = "tagruleid2";

  @Mock private TagRuleRepository tagRuleRepository;

  @Mock private AssetGroupRepository assetGroupRepository;

  @Mock private TagRepository tagRepository;

  @InjectMocks private TagRuleService tagRuleService;

  @Test
  void testFindById() {
    TagRule expected = TagRuleFixture.createTagRule(TAG_RULE_ID);
    when(tagRuleRepository.findById(TAG_RULE_ID)).thenReturn(Optional.of(expected));
    Optional<TagRule> result = tagRuleService.findById(TAG_RULE_ID);
    assertEquals(expected, result.get());
  }

  @Test
  void testFindAll() {
    List<TagRule> expected =
        List.of(
            TagRuleFixture.createTagRule(TAG_RULE_ID), TagRuleFixture.createTagRule(TAG_RULE_ID_2));
    when(tagRuleRepository.findAll()).thenReturn(expected);

    List<TagRule> result = tagRuleService.findAll();
    assertEquals(new HashSet<>(expected), new HashSet<>(result));
  }

  @Test
  void testDeleteTagRule() {
    TagRule expected = TagRuleFixture.createTagRule(TAG_RULE_ID);
    when(tagRuleRepository.findById(TAG_RULE_ID)).thenReturn(Optional.of(expected));
    tagRuleService.deleteTagRule(TAG_RULE_ID);
    verify(tagRuleRepository).deleteById(TAG_RULE_ID);
  }

  @Test
  void testDeleteTagRule_WITH_octi_rule() {
    TagRule expected = TagRuleFixture.createTagRule(TAG_RULE_ID);
    expected.getTag().setName("opencti");
    when(tagRuleRepository.findById(TAG_RULE_ID)).thenReturn(Optional.of(expected));

    assertThrows(
        ForbiddenException.class,
        () -> {
          tagRuleService.deleteTagRule(TAG_RULE_ID);
        });
  }

  @Test
  void testCreateTagRule() {
    TagRule expected = TagRuleFixture.createTagRule(TAG_RULE_ID);
    when(tagRuleRepository.save(any())).thenReturn(expected);
    when(tagRepository.findByName(expected.getTag().getName()))
        .thenReturn(Optional.of(TagFixture.getTag()));
    expected
        .getAssetGroups()
        .forEach(
            assetGroup ->
                when(assetGroupRepository.findById(assetGroup.getId()))
                    .thenReturn(Optional.of(assetGroup)));

    TagRule result =
        tagRuleService.createTagRule(
            expected.getTag().getName(),
            expected.getAssetGroups().stream().map(AssetGroup::getId).toList());
    assertEquals(expected, result);
  }

  @Test
  void testCreateTagRule_WITH_octi_tag() {
    TagRule expected = TagRuleFixture.createTagRule(TAG_RULE_ID);
    expected.getTag().setName("opencti");
    when(tagRuleRepository.save(any())).thenReturn(expected);
    when(tagRepository.findByName(expected.getTag().getName()))
        .thenReturn(Optional.of(TagFixture.getTag()));
    expected
        .getAssetGroups()
        .forEach(
            assetGroup ->
                when(assetGroupRepository.findById(assetGroup.getId()))
                    .thenReturn(Optional.of(assetGroup)));
    assertThrows(
        ForbiddenException.class,
        () -> {
          tagRuleService.createTagRule(
              expected.getTag().getName(),
              expected.getAssetGroups().stream().map(AssetGroup::getId).toList());
        });
  }

  @Test
  void testCreateTagRule_WITH_non_existing_tag() {
    TagRule expected = TagRuleFixture.createTagRule(TAG_RULE_ID);
    Tag tag = TagFixture.getTag();
    when(tagRuleRepository.save(any())).thenReturn(expected);
    when(tagRepository.findByName(expected.getTag().getName())).thenReturn(null);
    when(tagRepository.save(any())).thenReturn(tag);
    expected
        .getAssetGroups()
        .forEach(
            assetGroup ->
                when(assetGroupRepository.findById(assetGroup.getId()))
                    .thenReturn(Optional.of(assetGroup)));
    assertThrows(
        ElementNotFoundException.class,
        () -> {
          tagRuleService.createTagRule(
              expected.getId(), expected.getAssetGroups().stream().map(AssetGroup::getId).toList());
        });
  }

  @Test
  void testCreateTagRule_WITH_non_existing_asset() {
    TagRule expected = TagRuleFixture.createTagRule(TAG_RULE_ID);
    when(tagRuleRepository.save(any())).thenReturn(expected);
    when(tagRepository.findByName(expected.getTag().getName()))
        .thenReturn(Optional.of(TagFixture.getTag()));
    expected
        .getAssetGroups()
        .forEach(
            assetGroup ->
                when(assetGroupRepository.findById(assetGroup.getId()))
                    .thenReturn(Optional.empty()));
    assertThrows(
        ElementNotFoundException.class,
        () -> {
          TagRule result =
              tagRuleService.createTagRule(
                  expected.getId(),
                  expected.getAssetGroups().stream().map(AssetGroup::getId).toList());
        });
  }

  @Test
  void testUpdateTagRule() {
    TagRule beforeUpdate = TagRuleFixture.createTagRule(TAG_RULE_ID);
    TagRule expected = new TagRule();
    expected.setId(TAG_RULE_ID);
    expected.setTag(TagFixture.getTag("test"));

    when(tagRuleRepository.save(any())).thenReturn(expected);
    when(tagRepository.findByName(expected.getTag().getName()))
        .thenReturn(Optional.of(TagFixture.getTag()));
    when(tagRuleRepository.findById(beforeUpdate.getId())).thenReturn(Optional.of(beforeUpdate));
    expected
        .getAssetGroups()
        .forEach(
            assetGroup ->
                when(assetGroupRepository.findById(assetGroup.getId()))
                    .thenReturn(Optional.of(assetGroup)));

    TagRule result =
        tagRuleService.updateTagRule(
            expected.getId(),
            expected.getTag().getName(),
            expected.getAssetGroups().stream().map(AssetGroup::getId).toList());
    assertEquals(expected, result);
  }

  @Test
  void testUpdateTagRule_WITH_octi_tag() {
    TagRule beforeUpdate = TagRuleFixture.createTagRule(TAG_RULE_ID);
    beforeUpdate.getTag().setName("opencti");
    TagRule expected = new TagRule();
    expected.setId(TAG_RULE_ID);
    expected.setTag(TagFixture.getTag("test"));

    when(tagRuleRepository.save(any())).thenReturn(expected);
    when(tagRepository.findByName(expected.getTag().getName()))
        .thenReturn(Optional.of(TagFixture.getTag()));
    when(tagRuleRepository.findById(expected.getId())).thenReturn(Optional.of(beforeUpdate));
    expected
        .getAssetGroups()
        .forEach(
            assetGroup ->
                when(assetGroupRepository.findById(assetGroup.getId()))
                    .thenReturn(Optional.of(assetGroup)));
    assertThrows(
        ForbiddenException.class,
        () -> {
          tagRuleService.updateTagRule(
              expected.getId(),
              expected.getTag().getName(),
              expected.getAssetGroups().stream().map(AssetGroup::getId).toList());
        });
  }

  @Test
  void testUpdateTagRule_WITH_non_existing_tag() {
    TagRule expected = TagRuleFixture.createTagRule(TAG_RULE_ID);
    Tag tag = TagFixture.getTag();
    when(tagRuleRepository.save(any())).thenReturn(expected);
    when(tagRepository.findByName(expected.getTag().getName())).thenReturn(Optional.empty());
    when(tagRepository.save(any())).thenReturn(tag);
    when(tagRuleRepository.findById(expected.getId())).thenReturn(Optional.of(expected));
    expected
        .getAssetGroups()
        .forEach(
            assetGroup ->
                when(assetGroupRepository.findById(assetGroup.getId()))
                    .thenReturn(Optional.of(assetGroup)));
    assertThrows(
        ElementNotFoundException.class,
        () -> {
          tagRuleService.updateTagRule(
              expected.getId(),
              expected.getTag().getName(),
              expected.getAssetGroups().stream().map(AssetGroup::getId).toList());
        });
  }

  @Test
  void testUpdateTagRule_WITH_non_existing_asset_group() {
    TagRule expected = TagRuleFixture.createTagRule(TAG_RULE_ID);
    when(tagRuleRepository.save(any())).thenReturn(expected);
    when(tagRepository.findByName(expected.getTag().getName()))
        .thenReturn(Optional.of(TagFixture.getTag()));
    when(tagRuleRepository.findById(expected.getId())).thenReturn(Optional.of(expected));
    expected
        .getAssetGroups()
        .forEach(
            assetGroup ->
                when(assetGroupRepository.findById(assetGroup.getId()))
                    .thenReturn(Optional.empty()));
    assertThrows(
        ElementNotFoundException.class,
        () -> {
          tagRuleService.updateTagRule(
              expected.getId(),
              expected.getTag().getName(),
              expected.getAssetGroups().stream().map(AssetGroup::getId).toList());
        });
  }

  @Test
  void testUpdateTagRule_WITH_non_existing_tag_rule() {
    TagRule expected = TagRuleFixture.createTagRule(TAG_RULE_ID);
    when(tagRuleRepository.findById(expected.getId())).thenReturn(Optional.empty());
    assertThrows(
        ElementNotFoundException.class,
        () -> {
          tagRuleService.updateTagRule(
              expected.getId(),
              expected.getTag().getName(),
              expected.getAssetGroups().stream().map(AssetGroup::getId).toList());
        });
  }

  @Test
  void testGetAssetsFromTagIds() {
    List<String> tagIds = List.of("tag1");
    TagRule tagRule = TagRuleFixture.createTagRule(TAG_RULE_ID);
    when(tagRuleRepository.findByTags(tagIds)).thenReturn(List.of(tagRule));
    assertEquals(
        new HashSet<>(tagRule.getAssetGroups()),
        new HashSet<>(tagRuleService.getAssetGroupsFromTagIds(tagIds)));
  }

  @Test
  void testApplyTagRuleToInjectCreation() {
    AssetGroup assetGroup1 = AssetGroupFixture.createDefaultAssetGroup("assetgroup1");
    assetGroup1.setId("assetgroup1");
    AssetGroup assetGroup2 = AssetGroupFixture.createDefaultAssetGroup("assetgroup2");
    assetGroup2.setId("assetgroup2");
    AssetGroup assetGroup3 = AssetGroupFixture.createDefaultAssetGroup("assetgroup3");
    assetGroup3.setId("assetgroup3");
    AssetGroup assetGroup4 = AssetGroupFixture.createDefaultAssetGroup("assetgroup4");
    assetGroup4.setId("assetgroup4");

    Tag tag1 = TagFixture.getTag("tag2");
    Tag tag2 = TagFixture.getTag("tag3");

    List<AssetGroup> currentAssetGroups = List.of(assetGroup1, assetGroup2);
    List<AssetGroup> defaultAssetGroups = List.of(assetGroup2, assetGroup3, assetGroup4);
    TagRule tagRule = TagRuleFixture.createTagRule("tag_rule1", defaultAssetGroups);

    when(tagRuleRepository.findByTags(List.of(tag1.getId(), tag2.getId())))
        .thenReturn(List.of(tagRule));

    List<AssetGroup> result =
        tagRuleService.applyTagRuleToInjectCreation(
            List.of(tag1.getId(), tag2.getId()), currentAssetGroups);
    List<AssetGroup> expected = List.of(assetGroup1, assetGroup2, assetGroup3, assetGroup4);
    assertEquals(new HashSet<>(expected), new HashSet<>(result));
  }

  @Test
  void testCheckIfRulesApply_WITH_tag_rule_added() {
    List<String> newTagIds = List.of("tag1", "tag2");
    List<String> currentTagIds = List.of("tag2", "tag3");
    TagRule tagRule = TagRuleFixture.createTagRule(TAG_RULE_ID);
    when(tagRuleRepository.findByTags(List.of("tag1"))).thenReturn(List.of(tagRule));

    boolean result = tagRuleService.checkIfRulesApply(currentTagIds, newTagIds);

    assertTrue(result);
  }

  @Test
  void testCheckIfRulesApply_WITH_no_rules_to_apply() {
    List<String> newTagIds = List.of("tag1", "tag2");
    List<String> currentTagIds = List.of("tag2", "tag1");
    TagRule tagRule = TagRuleFixture.createTagRule(TAG_RULE_ID);
    when(tagRuleRepository.findByTags(List.of("tag2"))).thenReturn(List.of(tagRule));
    when(tagRuleRepository.findByTags(List.of("tag1"))).thenReturn(List.of(tagRule));
    boolean result = tagRuleService.checkIfRulesApply(currentTagIds, newTagIds);
    assertFalse(result);
  }

  private AssetGroup getAssetGroup(String name) {
    AssetGroup assetGroup = AssetGroupFixture.createDefaultAssetGroup(name);
    assetGroup.setId(name);
    return assetGroup;
  }
}
