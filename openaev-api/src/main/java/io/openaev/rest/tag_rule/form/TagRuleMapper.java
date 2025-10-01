package io.openaev.rest.tag_rule.form;

import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.TagRule;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class TagRuleMapper {
  public TagRuleOutput toTagRuleOutput(final TagRule tagRule) {
    return TagRuleOutput.builder()
        .id(tagRule.getId())
        .tagName(tagRule.getTag().getName())
        .assetGroups(
            tagRule.getAssetGroups().stream()
                .collect(Collectors.toMap(AssetGroup::getId, AssetGroup::getName)))
        .build();
  }
}
