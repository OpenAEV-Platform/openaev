package io.openaev.utils.mapper;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.*;
import io.openaev.healthcheck.dto.HealthCheck;
import io.openaev.healthcheck.utils.HealthCheckUtils;
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

/**
 * Mapper component for converting Inject entities to various output DTOs.
 *
 * <p>Provides comprehensive mapping methods for transforming inject domain objects into API
 * response objects, including result overviews, simple representations, and target mappings.
 *
 * @see io.openaev.database.model.Inject
 * @see io.openaev.rest.inject.output.InjectOutput
 */
@Component
@RequiredArgsConstructor
public class InjectMapper {

  private final InjectStatusMapper injectStatusMapper;
  private final InjectExpectationMapper injectExpectationMapper;
  private final InjectUtils injectUtils;
  private final HealthCheckUtils healthCheckUtils;

  /**
   * Converts an inject to a result overview output containing full execution details.
   *
   * <p>Includes inject metadata, status, expectations, kill chain phases, and aggregated
   * expectation results by type.
   *
   * @param inject the inject to convert
   * @return the inject result overview output DTO
   */
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
        .isReady(healthCheckUtils.runContentChecks(inject).isEmpty())
        .updatedAt(inject.getUpdatedAt())
        .build();
  }

  // -- OBJECT[] to TARGETSIMPLE --

  /**
   * Converts raw database result arrays to target simple DTOs.
   *
   * @param targets the raw query results containing target data
   * @param type the type of targets being converted
   * @return list of target simple DTOs
   */
  public List<TargetSimple> toTargetSimple(List<Object[]> targets, TargetType type) {
    return targets.stream()
        .filter(Objects::nonNull)
        .map(target -> toTargetSimple(target, type))
        .toList();
  }

  /**
   * Converts a single raw database result array to a target simple DTO.
   *
   * @param target array containing [exerciseId, targetId, targetName]
   * @param type the type of target
   * @return the target simple DTO
   */
  public TargetSimple toTargetSimple(Object[] target, TargetType type) {
    return TargetSimple.builder()
        .id((String) target[1])
        .name((String) target[2])
        .type(type)
        .build();
  }

  // -- INJECTORCONTRACT to INJECTORCONTRACT SIMPLE --

  /**
   * Converts an optional injector contract to its output representation.
   *
   * @param injectorContract the optional injector contract
   * @return the injector contract output DTO, or null if not present
   */
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
                    .domains(
                        payloadToSimple.getDomains().stream()
                            .map(Domain::getId)
                            .toArray(String[]::new))
                    .build())
        .orElse(null);
  }

  // -- EXPECTATIONS to EXPECTATIONSIMPLE

  /**
   * Converts a list of inject expectations to simplified DTOs.
   *
   * @param expectations the expectations to convert
   * @return list of simplified expectation DTOs
   */
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

  /**
   * Converts a list of kill chain phases to simplified DTOs.
   *
   * @param killChainPhases the kill chain phases to convert
   * @return list of simplified kill chain phase DTOs
   */
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

  /**
   * Converts an inject to a simplified representation.
   *
   * @param inject the inject to convert
   * @return the simplified inject DTO
   */
  public InjectSimple toInjectSimple(Inject inject) {
    return InjectSimple.builder().id(inject.getId()).title(inject.getTitle()).build();
  }

  /**
   * Converts a set of injects to related entity outputs.
   *
   * <p>Used for showing inject references in document or other entity contexts.
   *
   * @param injects the injects to convert
   * @return set of related entity output DTOs
   */
  public static Set<RelatedEntityOutput> toRelatedEntityOutputs(Set<Inject> injects) {
    return injects.stream()
        .map(inject -> toRelatedEntityOutput(inject))
        .collect(Collectors.toSet());
  }

  private static RelatedEntityOutput toRelatedEntityOutput(Inject inject) {
    return RelatedEntityOutput.builder().id(inject.getId()).name(inject.getTitle()).build();
  }

  /**
   * Creates an inject output DTO from individual components.
   *
   * <p>Assembles an inject output from raw data components, typically from database query results.
   * Calculates readiness based on contract requirements and target assignments.
   *
   * @param id the inject ID
   * @param title the inject title
   * @param enabled whether the inject is enabled
   * @param content the inject content as JSON
   * @param allTeams whether all teams are targeted
   * @param exerciseId the parent exercise ID
   * @param scenarioId the parent scenario ID
   * @param dependsDuration the duration dependency
   * @param injectorContract the injector contract
   * @param tags array of tag IDs
   * @param teams array of team IDs
   * @param assets array of asset IDs
   * @param assetGroups array of asset group IDs
   * @param injectType the inject type identifier
   * @param injectDependency the inject dependency if any
   * @return the assembled inject output DTO
   */
  public InjectOutput toInjectOutput(
      String id,
      String title,
      String description,
      String country,
      String city,
      boolean enabled,
      Instant triggerNowDate,
      ObjectNode content,
      Instant createdAt,
      Instant updatedAt,
      boolean allTeams,
      Exercise exercise,
      Scenario scenario,
      List<InjectDependency> dependsOn,
      Long dependsDuration,
      InjectorContract injectorContract,
      User user,
      InjectStatus status,
      CollectExecutionStatus collectExecutionStatus,
      Set<Tag> tags,
      List<Team> teams,
      List<Asset> assets,
      List<AssetGroup> assetGroups,
      List<InjectDocument> documents,
      List<Communication> communications,
      List<InjectExpectation> expectations,
      Long numberOfTargetUsers,
      Instant date,
      Long communicationsNumber,
      Long communicationsNotAckNumber,
      Instant sentAt,
      List<KillChainPhase> killChainPhases,
      List<AttackPattern> attackPatterns,
      String type,
      List<HealthCheck> healthchecks) {
    InjectOutput injectOutput = new InjectOutput();
    injectOutput.setId(id);
    injectOutput.setTitle(title);
    injectOutput.setDescription(description);
    injectOutput.setCountry(country);
    injectOutput.setCity(city);
    injectOutput.setEnabled(enabled);
    injectOutput.setTriggerNowDate(triggerNowDate);
    injectOutput.setContent(content);
    injectOutput.setCreatedAt(createdAt);
    injectOutput.setUpdatedAt(updatedAt);
    injectOutput.setAllTeams(allTeams);
    injectOutput.setExercise(exercise);
    injectOutput.setScenario(scenario);
    injectOutput.setDependsOn(dependsOn);
    injectOutput.setDependsDuration(dependsDuration);
    injectOutput.setInjectorContract(injectorContract);
    injectOutput.setUser(user);
    injectOutput.setStatus(status);
    injectOutput.setCollectExecutionStatus(collectExecutionStatus);
    injectOutput.setTags(tags);
    injectOutput.setTeams(teams);
    injectOutput.setAssets(assets);
    injectOutput.setAssetGroups(assetGroups);
    injectOutput.setDocuments(documents);
    injectOutput.setCommunications(communications);
    injectOutput.setExpectations(expectations);
    injectOutput.setNumberOfTargetUsers(numberOfTargetUsers);
    injectOutput.setDate(date);
    injectOutput.setCommunicationsNumber(communicationsNumber);
    injectOutput.setCommunicationsNotAckNumber(communicationsNotAckNumber);
    injectOutput.setSentAt(sentAt);
    injectOutput.setKillChainPhases(killChainPhases);
    injectOutput.setAttackPatterns(attackPatterns);
    injectOutput.setType(type);
    injectOutput.setHealthchecks(healthchecks);
    return injectOutput;
  }

  public InjectOutput toInjectOutput(Inject inject, List<HealthCheck> healthchecks) {
    return toInjectOutput(
        inject.getId(),
        inject.getTitle(),
        inject.getDescription(),
        inject.getCountry(),
        inject.getCity(),
        inject.isEnabled(),
        inject.getTriggerNowDate(),
        inject.getContent(),
        inject.getCreatedAt(),
        inject.getUpdatedAt(),
        inject.isAllTeams(),
        inject.getExercise(),
        inject.getScenario(),
        inject.getDependsOn(),
        inject.getDependsDuration(),
        inject.getInjectorContract().orElse(null),
        inject.getUser(),
        inject.getStatus().orElse(null),
        inject.getCollectExecutionStatus(),
        inject.getTags(),
        inject.getTeams(),
        inject.getAssets(),
        inject.getAssetGroups(),
        inject.getDocuments(),
        inject.getCommunications(),
        inject.getExpectations(),
        inject.getNumberOfTargetUsers(),
        inject.getDate().orElse(null),
        inject.getCommunicationsNumber(),
        inject.getCommunicationsNotAckNumber(),
        inject.getSentAt(),
        inject.getKillChainPhases(),
        inject.getAttackPatterns(),
        inject.getType(),
        healthchecks);
  }
}
