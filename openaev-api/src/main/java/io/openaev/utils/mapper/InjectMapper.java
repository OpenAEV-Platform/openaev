package io.openaev.utils.mapper;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.*;
import io.openaev.helper.InjectModelHelper;
import io.openaev.rest.atomic_testing.form.*;
import io.openaev.rest.document.form.RelatedEntityOutput;
import io.openaev.rest.inject.output.InjectOutput;
import io.openaev.rest.inject.output.InjectSimple;
import io.openaev.rest.payload.output.PayloadSimple;
import io.openaev.utils.InjectExpectationResultUtils;
import io.openaev.utils.InjectUtils;
import io.openaev.utils.TargetType;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InjectMapper {

  private final InjectStatusMapper injectStatusMapper;
  private final InjectExpectationMapper injectExpectationMapper;
  private final InjectUtils injectUtils;

  public InjectResultOverviewOutput toInjectResultOverviewOutput(Inject inject) {
    // --
    Optional<InjectorContract> injectorContract = inject.getInjectorContract();

    List<String> documentIds =
        inject.getDocuments().stream()
            .map(InjectDocument::getDocument)
            .map(Document::getId)
            .toList();

    return InjectResultOverviewOutput.builder()
        .id(inject.getId())
        .title(inject.getTitle())
        .description(inject.getDescription())
        .content(inject.getContent())
        .type(injectorContract.map(contract -> contract.getInjector().getType()).orElse(null))
        .tagIds(inject.getTags().stream().map(Tag::getId).toList())
        .documentIds(documentIds)
        .injectorContract(toInjectorContractOutput(injectorContract))
        .status(injectStatusMapper.toInjectStatusSimple(inject.getStatus()))
        .expectations(toInjectExpectationSimples(inject.getExpectations()))
        .killChainPhases(toKillChainPhasesSimples(inject.getKillChainPhases()))
        .tags(inject.getTags().stream().map(Tag::getId).collect(Collectors.toSet()))
        .expectationResultByTypes(
            injectExpectationMapper.extractExpectationResults(
                inject.getContent(),
                injectUtils.getPrimaryExpectations(inject),
                InjectExpectationResultUtils::getScores))
        .isReady(inject.isReady())
        .updatedAt(inject.getUpdatedAt())
        .build();
  }

  // -- OBJECT[] to TARGETSIMPLE --
  public List<TargetSimple> toTargetSimple(List<Object[]> targets, TargetType type) {
    return targets.stream()
        .filter(Objects::nonNull)
        .map(target -> toTargetSimple(target, type))
        .toList();
  }

  public TargetSimple toTargetSimple(Object[] target, TargetType type) {
    return TargetSimple.builder()
        .id((String) target[1])
        .name((String) target[2])
        .type(type)
        .build();
  }

  // -- INJECTORCONTRACT to INJECTORCONTRACT SIMPLE --
  public AtomicInjectorContractOutput toInjectorContractOutput(
      Optional<InjectorContract> injectorContract) {
    return injectorContract
        .map(
            contract ->
                AtomicInjectorContractOutput.builder()
                    .id(contract.getId())
                    .content(contract.getContent())
                    .convertedContent(contract.getConvertedContent())
                    .platforms(contract.getPlatforms())
                    .payload(toPayloadSimple(Optional.ofNullable(contract.getPayload())))
                    .labels(contract.getLabels())
                    .build())
        .orElse(null);
  }

  private PayloadSimple toPayloadSimple(Optional<Payload> payload) {
    return payload
        .map(
            payloadToSimple ->
                PayloadSimple.builder()
                    .id(payloadToSimple.getId())
                    .type(payloadToSimple.getType())
                    .collectorType(payloadToSimple.getCollectorType())
                    .build())
        .orElse(null);
  }

  // -- EXPECTATIONS to EXPECTATIONSIMPLE
  public List<InjectExpectationSimple> toInjectExpectationSimples(
      List<InjectExpectation> expectations) {
    return expectations.stream().filter(Objects::nonNull).map(this::toExpectationSimple).toList();
  }

  private InjectExpectationSimple toExpectationSimple(InjectExpectation expectation) {
    return InjectExpectationSimple.builder()
        .id(expectation.getId())
        .name(expectation.getName())
        .build();
  }

  // -- KILLCHAINPHASES to KILLCHAINPHASESSIMPLE
  public List<KillChainPhaseSimple> toKillChainPhasesSimples(List<KillChainPhase> killChainPhases) {
    return killChainPhases.stream()
        .filter(Objects::nonNull)
        .map(this::toKillChainPhasesSimple)
        .toList();
  }

  private KillChainPhaseSimple toKillChainPhasesSimple(KillChainPhase killChainPhase) {
    return KillChainPhaseSimple.builder()
        .id(killChainPhase.getId())
        .name(killChainPhase.getName())
        .build();
  }

  public InjectSimple toInjectSimple(Inject inject) {
    return InjectSimple.builder().id(inject.getId()).title(inject.getTitle()).build();
  }

  public static Set<RelatedEntityOutput> toRelatedEntityOutputs(Set<Inject> injects) {
    return injects.stream()
        .map(inject -> toRelatedEntityOutput(inject))
        .collect(Collectors.toSet());
  }

  private static RelatedEntityOutput toRelatedEntityOutput(Inject inject) {
    return RelatedEntityOutput.builder().id(inject.getId()).name(inject.getTitle()).build();
  }

  public InjectOutput toInjectOutput(
      String id,
      String title,
      boolean enabled,
      ObjectNode content,
      boolean allTeams,
      String exerciseId,
      String scenarioId,
      Long dependsDuration,
      InjectorContract injectorContract,
      String[] tags,
      String[] teams,
      String[] assets,
      String[] assetGroups,
      String injectType,
      InjectDependency injectDependency) {
    InjectOutput injectOutput = new InjectOutput();
    injectOutput.setId(id);
    injectOutput.setTitle(title);
    injectOutput.setEnabled(enabled);
    injectOutput.setExercise(exerciseId);
    injectOutput.setScenario(scenarioId);
    injectOutput.setDependsDuration(dependsDuration);
    injectOutput.setInjectorContract(injectorContract);
    injectOutput.setTags(tags != null ? new HashSet<>(Arrays.asList(tags)) : new HashSet<>());
    injectOutput.setTeams(
        teams != null ? new ArrayList<>(Arrays.asList(teams)) : new ArrayList<>());
    injectOutput.setAssets(
        assets != null ? new ArrayList<>(Arrays.asList(assets)) : new ArrayList<>());
    injectOutput.setAssetGroups(
        assetGroups != null ? new ArrayList<>(Arrays.asList(assetGroups)) : new ArrayList<>());
    injectOutput.setReady(
        InjectModelHelper.isReady(
            injectorContract,
            content,
            allTeams,
            injectOutput.getTeams(),
            injectOutput.getAssets(),
            injectOutput.getAssetGroups()));
    injectOutput.setInjectType(injectType);
    injectOutput.setTeams(
        teams != null ? new ArrayList<>(Arrays.asList(teams)) : new ArrayList<>());
    injectOutput.setContent(content);
    if (injectDependency != null) {
      injectOutput.setDependsOn(List.of(injectDependency));
    }
    return injectOutput;
  }
}
