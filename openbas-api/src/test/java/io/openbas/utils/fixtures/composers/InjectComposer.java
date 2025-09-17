package io.openbas.utils.fixtures.composers;

import io.openbas.database.model.*;
import io.openbas.database.repository.InjectDocumentRepository;
import io.openbas.database.repository.InjectRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class InjectComposer extends ComposerBase<Inject> {
  @Autowired private InjectRepository injectRepository;
  @Autowired private InjectDocumentRepository injectDocumentRepository;

  public class Composer extends InnerComposerBase<Inject> {
    private final Inject inject;
    private Optional<InjectorContractComposer.Composer> injectorContractComposer = Optional.empty();
    private final List<TagComposer.Composer> tagComposers = new ArrayList<>();
    private final List<EndpointComposer.Composer> endpointComposers = new ArrayList<>();
    private Optional<InjectStatusComposer.Composer> injectStatusComposers = Optional.empty();
    private Optional<InjectTestStatusComposer.Composer> injectTestStatusComposers =
        Optional.empty();
    private final List<DocumentComposer.Composer> documentComposers = new ArrayList<>();
    private final List<TeamComposer.Composer> teamComposers = new ArrayList<>();
    private final List<AssetGroupComposer.Composer> assetGroupComposers = new ArrayList<>();
    private final List<InjectExpectationComposer.Composer> expectationComposers = new ArrayList<>();
    private final List<FindingComposer.Composer> findingComposers = new ArrayList<>();

    public Composer(Inject inject) {
      this.inject = inject;
    }

    public Composer withTag(TagComposer.Composer tagComposer) {
      tagComposers.add(tagComposer);
      Set<Tag> tempTags = this.inject.getTags();
      tempTags.add(tagComposer.get());
      this.inject.setTags(tempTags);
      return this;
    }

    public Composer withInjectorContract(
        InjectorContractComposer.Composer injectorContractComposer) {
      this.injectorContractComposer = Optional.of(injectorContractComposer);
      this.inject.setInjectorContract(injectorContractComposer.get());
      return this;
    }

    public Composer withDocument(DocumentComposer.Composer documentComposer) {
      this.documentComposers.add(documentComposer);
      List<InjectDocument> tempDocs = this.inject.getDocuments();
      InjectDocument newDoc = new InjectDocument();
      newDoc.setDocument(documentComposer.get());
      newDoc.setInject(this.inject);
      tempDocs.add(newDoc);
      this.inject.setDocuments(tempDocs);
      return this;
    }

    public Composer withTeam(TeamComposer.Composer teamComposer) {
      this.teamComposers.add(teamComposer);
      List<Team> tempTeams = this.inject.getTeams();
      tempTeams.add(teamComposer.get());
      this.inject.setTeams(tempTeams);
      return this;
    }

    public Composer withId(String id) {
      this.inject.setId(id);
      return this;
    }

    public Composer withInjectStatus(InjectStatusComposer.Composer injectStatus) {
      injectStatusComposers = Optional.of(injectStatus);
      injectStatus.get().setInject(this.inject);
      this.inject.setStatus(injectStatus.get());
      return this;
    }

    public Composer withInjectTestStatus(InjectTestStatusComposer.Composer injectTestStatus) {
      injectTestStatusComposers = Optional.of(injectTestStatus);
      injectTestStatus.get().setInject(this.inject);
      return this;
    }

    public Composer withEndpoint(EndpointComposer.Composer endpointComposer) {
      endpointComposers.add(endpointComposer);
      List<Asset> assets = inject.getAssets();
      assets.add(endpointComposer.get());
      this.inject.setAssets(assets);
      return this;
    }

    public Composer withAssetGroup(AssetGroupComposer.Composer assetGroupComposer) {
      assetGroupComposers.add(assetGroupComposer);
      List<AssetGroup> tempAssetGroups = this.inject.getAssetGroups();
      tempAssetGroups.add(assetGroupComposer.get());
      this.inject.setAssetGroups(tempAssetGroups);
      return this;
    }

    public Composer withExpectation(InjectExpectationComposer.Composer expectationComposer) {
      expectationComposers.add(expectationComposer);
      List<InjectExpectation> tempExpectations = this.inject.getExpectations();
      tempExpectations.add(expectationComposer.get());
      expectationComposer.get().setInject(this.inject);
      this.inject.setExpectations(tempExpectations);
      return this;
    }

    public Composer withDependsOn(Inject injectParent) {
      InjectDependency injectDependency = new InjectDependency();
      InjectDependencyId injectDependencyId = new InjectDependencyId();
      injectDependencyId.setInjectParent(injectParent);
      injectDependencyId.setInjectChildren(this.inject);
      injectDependency.setCompositeId(injectDependencyId);
      this.inject.setDependsOn(new ArrayList<>(List.of(injectDependency)));
      return this;
    }

    public Composer withFinding(FindingComposer.Composer findingComposer) {
      findingComposers.add(findingComposer);
      List<Finding> tmpFindings = this.inject.getFindings();
      tmpFindings.add(findingComposer.get());
      findingComposer.get().setInject(this.inject);
      this.inject.setFindings(tmpFindings);
      return this;
    }

    @Override
    public Composer persist() {
      this.injectorContractComposer.ifPresent(
          composer -> {
            composer.persist();
            if (this.inject.getContent() == null) {
              this.inject.setContent(composer.getInjectContent());
            }
          });
      assetGroupComposers.forEach(AssetGroupComposer.Composer::persist);
      endpointComposers.forEach(EndpointComposer.Composer::persist);
      tagComposers.forEach(TagComposer.Composer::persist);
      teamComposers.forEach(TeamComposer.Composer::persist);
      documentComposers.forEach(DocumentComposer.Composer::persist);
      injectRepository.save(inject);
      injectStatusComposers.ifPresent(InjectStatusComposer.Composer::persist);
      expectationComposers.forEach(InjectExpectationComposer.Composer::persist);
      findingComposers.forEach(FindingComposer.Composer::persist);
      injectDocumentRepository.saveAll(inject.getDocuments());
      return this;
    }

    @Override
    public Composer delete() {
      injectRepository.delete(inject);
      documentComposers.forEach(DocumentComposer.Composer::delete);
      tagComposers.forEach(TagComposer.Composer::delete);
      endpointComposers.forEach(EndpointComposer.Composer::delete);
      assetGroupComposers.forEach(AssetGroupComposer.Composer::delete);
      injectStatusComposers.ifPresent(InjectStatusComposer.Composer::delete);
      teamComposers.forEach(TeamComposer.Composer::delete);
      injectorContractComposer.ifPresent(InjectorContractComposer.Composer::delete);
      findingComposers.forEach(FindingComposer.Composer::delete);
      expectationComposers.forEach(InjectExpectationComposer.Composer::delete);
      return this;
    }

    @Override
    public Inject get() {
      return this.inject;
    }
  }

  public Composer forInject(Inject inject) {
    generatedItems.add(inject);
    return new Composer(inject);
  }
}
