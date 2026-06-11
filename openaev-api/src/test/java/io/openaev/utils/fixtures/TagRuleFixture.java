package io.openaev.utils.fixtures;

import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.Tag;
import io.openaev.database.model.TagRule;
import io.openaev.rest.tag_rule.form.TagRuleInput;
import io.openaev.rest.tag_rule.form.TagRuleOutput;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class TagRuleFixture {
  public static final String TAG_RULE_ID = "tagruleid";
  public static final String TAG_RULE_ID_2 = "tagruleid2";
  public static final String TAG_NAME = "testtag";
  public static final String ASSET_GROUP_ID_1 = "assetgroup1";
  public static final String ASSET_GROUP_ID_2 = "assetgroup2";
  public static final String ASSET_GROUP_NAME_1 = "name 1";
  public static final String ASSET_GROUP_NAME_2 = "name 2";

  public static TagRule createTagRule(String tagRuleId) {

    Tag tag = Tag.fromTenant("tenant");
    tag.setName(TAG_NAME);

    AssetGroup assetGroup1 = AssetGroup.fromTenant("tenant");
    assetGroup1.setId(ASSET_GROUP_ID_1);
    assetGroup1.setName(ASSET_GROUP_NAME_1);
    AssetGroup assetGroup2 = AssetGroup.fromTenant("tenant");
    assetGroup2.setId(ASSET_GROUP_ID_2);
    assetGroup2.setName(ASSET_GROUP_NAME_2);

    TagRule rule = TagRule.fromTenant("tenant");
    rule.setAssetGroups(new ArrayList<>(Arrays.asList(assetGroup1, assetGroup2)));
    rule.setTag(tag);
    rule.setId(tagRuleId);

    return rule;
  }

  public static TagRule createTagRule(String tagRuleId, List<AssetGroup> assetGroups) {
    Tag tag = Tag.fromTenant("tenant");
    tag.setName(TAG_NAME);

    TagRule rule = TagRule.fromTenant("tenant");
    rule.setAssetGroups(assetGroups);
    rule.setTag(tag);
    rule.setId(tagRuleId);

    return rule;
  }

  public static TagRule createDefaultTagRule() {
    return TagRule.fromTenant("tenant");
  }

  public static TagRuleOutput createTagRuleOutput() {
    return TagRuleOutput.builder()
        .tagName(TAG_NAME)
        .assetGroups(
            Map.ofEntries(
                Map.entry(ASSET_GROUP_ID_1, ASSET_GROUP_NAME_1),
                Map.entry(ASSET_GROUP_ID_2, ASSET_GROUP_NAME_1)))
        .build();
  }

  public static TagRuleInput createTagRuleInput() {
    return TagRuleInput.builder()
        .tagName(TagRuleFixture.TAG_NAME)
        .assetGroups(List.of(ASSET_GROUP_ID_1, ASSET_GROUP_ID_2))
        .build();
  }
}
