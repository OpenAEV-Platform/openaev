package io.openbas.utils;

import static java.util.Collections.emptyList;

import io.openbas.database.model.AttackPattern;
import io.openbas.database.model.Inject;
import io.openbas.database.raw.*;
import io.openbas.database.repository.*;
import io.openbas.rest.inject.form.InjectExpectationResultsByAttackPattern;
import io.openbas.service.AssetGroupService;
import io.openbas.utils.AtomicTestingUtils.ExpectationResultsByType;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ResultUtils {

  private final InjectExpectationRepository injectExpectationRepository;
  private final TeamRepository teamRepository;
  private final UserRepository userRepository;
  private final AssetRepository assetRepository;
  private final AssetGroupRepository assetGroupRepository;
  private final AssetGroupService assetGroupService;
  private final AgentRepository agentRepository;

  // -- UTILS --
  public List<ExpectationResultsByType> getResultsByTypes(Set<String> injectIds) {
    if (injectIds == null || injectIds.isEmpty()) {
      return emptyList();
    }
    return computeGlobalExpectationResults(injectIds);
  }

  public static List<InjectExpectationResultsByAttackPattern> computeInjectExpectationResults(
      @NotNull final List<Inject> injects) {

    Map<AttackPattern, List<Inject>> groupedByAttackPattern =
        injects.stream()
            .flatMap(
                inject ->
                    inject
                        .getInjectorContract()
                        .map(
                            contract ->
                                contract.getAttackPatterns().stream()
                                    .map(attackPattern -> Map.entry(attackPattern, inject)))
                        .orElseGet(Stream::empty))
            .collect(
                Collectors.groupingBy(
                    Map.Entry::getKey,
                    Collectors.mapping(Map.Entry::getValue, Collectors.toList())));

    return groupedByAttackPattern.entrySet().stream()
        .map(entry -> new InjectExpectationResultsByAttackPattern(entry.getKey(), entry.getValue()))
        .toList();
  }

  // -- GLOBAL SCORE --
  public List<ExpectationResultsByType> computeGlobalExpectationResults(
      @NotNull Set<String> injectIds) {
    return AtomicTestingUtils.getExpectationResultByTypesFromRaw(
        injectExpectationRepository.rawForComputeGlobalByInjectIds(injectIds));
  }
}
