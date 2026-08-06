package io.openaev.utils.mapper;

import io.openaev.database.model.*;
import io.openaev.database.repository.FindingRepository;
import io.openaev.rest.finding.form.AggregatedFindingOutput;
import io.openaev.rest.finding.form.RelatedFindingOutput;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
public class FindingMapper {

  private final FindingRepository findingRepository;
  private final EndpointMapper endpointMapper;
  private final AssetGroupMapper assetGroupMapper;
  private final ExerciseMapper exerciseMapper;
  private final ScenarioMapper scenarioMapper;
  private final InjectMapper injectMapper;
  private final InjectorMapper injectorMapper;

  public AggregatedFindingOutput toAggregatedFindingOutput(
      Finding finding, List<Asset> relatedAssets) {
    return AggregatedFindingOutput.builder()
        .id(finding.getId())
        .value(finding.getValue())
        .type(finding.getType())
        .creationDate(finding.getCreationDate())
        .updateDate(finding.getUpdateDate())
        // Findings can attach to ANY asset type (agentless websites, AI targets, cloud/network
        // assets), so no instanceof Endpoint filtering here.
        .assets(
            relatedAssets.stream()
                .map(endpointMapper::toEndpointSimple)
                .collect(Collectors.toSet()))
        .source(
            Optional.ofNullable(finding.getInject())
                .map(Inject::getInjector)
                .map(injectorMapper::toInjectorSimple)
                .orElse(null))
        .build();
  }

  public RelatedFindingOutput toRelatedFindingOutput(Finding finding) {
    return RelatedFindingOutput.builder()
        .id(finding.getId())
        .value(finding.getValue())
        .type(finding.getType())
        .updateDate(finding.getUpdateDate())
        .assets(
            finding.getAssets().stream()
                .map(asset -> endpointMapper.toEndpointSimple(asset))
                .collect(Collectors.toSet()))
        .assetGroups(
            finding.getAssetGroups().stream()
                .map(assetGroup -> assetGroupMapper.toAssetGroupSimple(assetGroup))
                .collect(Collectors.toSet()))
        .inject(injectMapper.toInjectSimple(finding.getInject()))
        .simulation(
            Optional.ofNullable(finding.getInject().getExercise())
                .map(exercise -> exerciseMapper.toExerciseSimple(exercise))
                .orElse(null))
        .scenario(
            Optional.ofNullable(finding.getInject().getExercise())
                .map(Exercise::getScenario)
                .map(scenario -> scenarioMapper.toScenarioSimple(scenario))
                .orElse(null))
        .source(
            Optional.ofNullable(finding.getInject())
                .map(Inject::getInjector)
                .map(injectorMapper::toInjectorSimple)
                .orElse(null))
        .creationDate(finding.getCreationDate())
        .build();
  }
}
