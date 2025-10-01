package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.Asset;
import io.openaev.database.model.Finding;
import io.openaev.database.model.Tag;
import io.openaev.database.repository.FindingRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FindingComposer extends ComposerBase<Finding> {

  @Autowired private FindingRepository findingRepository;

  public class Composer extends InnerComposerBase<Finding> {

    private final Finding finding;
    private Optional<InjectComposer.Composer> injectComposer = Optional.empty();
    private final List<EndpointComposer.Composer> endpointComposers = new ArrayList<>();
    private final List<TagComposer.Composer> tagComposers = new ArrayList<>();

    public Composer(Finding finding) {
      this.finding = finding;
    }

    public Composer withInject(InjectComposer.Composer injectComposer) {
      this.injectComposer = Optional.of(injectComposer);
      this.finding.setInject(injectComposer.get());
      return this;
    }

    public Composer withEndpoint(EndpointComposer.Composer endpointComposer) {
      endpointComposers.add(endpointComposer);
      List<Asset> assets = finding.getAssets();
      assets.add(endpointComposer.get());
      this.finding.setAssets(assets);
      return this;
    }

    public Composer withTag(TagComposer.Composer tagComposer) {
      tagComposers.add(tagComposer);
      Set<Tag> tempTags = this.finding.getTags();
      tempTags.add(tagComposer.get());
      this.finding.setTags(tempTags);
      return this;
    }

    @Override
    public FindingComposer.Composer persist() {
      injectComposer.ifPresent(InjectComposer.Composer::persist);
      endpointComposers.forEach(EndpointComposer.Composer::persist);
      tagComposers.forEach(TagComposer.Composer::persist);
      findingRepository.save(this.finding);
      return this;
    }

    @Override
    public FindingComposer.Composer delete() {
      injectComposer.ifPresent(InjectComposer.Composer::delete);
      endpointComposers.forEach(EndpointComposer.Composer::delete);
      tagComposers.forEach(TagComposer.Composer::delete);
      findingRepository.delete(this.finding);
      return this;
    }

    @Override
    public Finding get() {
      return this.finding;
    }
  }

  public FindingComposer.Composer forFinding(Finding finding) {
    generatedItems.add(finding);
    return new FindingComposer.Composer(finding);
  }
}
