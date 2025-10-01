package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.TagRule;
import io.openaev.database.repository.TagRepository;
import io.openaev.database.repository.TagRuleRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TagRuleComposer extends ComposerBase<TagRule> {
  @Autowired private TagRuleRepository tagRuleRepository;
  @Autowired private TagRepository tagRepository;

  public class Composer extends InnerComposerBase<TagRule> {
    private final TagRule tagRule;
    private Optional<TagComposer.Composer> tagComposers = Optional.empty();
    private final List<AssetGroupComposer.Composer> assetGroupComposers = new ArrayList<>();

    public Composer(TagRule tagRule) {
      this.tagRule = tagRule;
    }

    public Composer withId(String id) {
      this.tagRule.setId(id);
      return this;
    }

    public TagRuleComposer.Composer withTag(TagComposer.Composer tagComposer) {
      tagComposers = Optional.of(tagComposer);
      this.tagRule.setTag(tagComposer.get());
      return this;
    }

    public TagRuleComposer.Composer withAssetGroup(AssetGroupComposer.Composer assetGroupComposer) {
      assetGroupComposers.add(assetGroupComposer);
      List<AssetGroup> tempAssetGroups = this.tagRule.getAssetGroups();
      tempAssetGroups.add(assetGroupComposer.get());
      this.tagRule.setAssetGroups(tempAssetGroups);
      return this;
    }

    @Override
    public Composer persist() {
      assetGroupComposers.forEach(AssetGroupComposer.Composer::persist);
      tagComposers.ifPresent(TagComposer.Composer::persist);
      tagRuleRepository.save(tagRule);
      return this;
    }

    @Override
    public Composer delete() {
      assetGroupComposers.forEach(AssetGroupComposer.Composer::delete);
      tagComposers.ifPresent(TagComposer.Composer::delete);
      tagRuleRepository.delete(tagRule);
      return this;
    }

    @Override
    public TagRule get() {
      return this.tagRule;
    }
  }

  public Composer forTagRule(TagRule tagRule) {
    generatedItems.add(tagRule);
    return new Composer(tagRule);
  }
}
