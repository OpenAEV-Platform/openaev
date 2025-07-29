package io.openbas.utils;

import static java.util.Collections.emptyList;

import io.openbas.database.model.AttackPattern;
import io.openbas.database.model.Inject;
import io.openbas.database.raw.RawInjectExpectation;
import io.openbas.database.repository.InjectExpectationRepository;
import io.openbas.rest.inject.form.InjectExpectationResultsByAttackPattern;
import io.openbas.utils.InjectExpectationResultUtils.ExpectationResultsByType;
import io.openbas.utils.mapper.InjectExpectationMapper;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ResultUtils {

  private final InjectExpectationRepository injectExpectationRepository;
  private final InjectExpectationMapper injectExpectationMapper;

  public List<ExpectationResultsByType> getResultsByTypes(Set<String> injectIds) {
    return computeGlobalExpectationResults(injectIds);
  }

  public List<ExpectationResultsByType> computeGlobalExpectationResults(Set<String> injectIds) {

    if (injectIds == null || injectIds.isEmpty()) {
      return emptyList();
    }

    List<RawInjectExpectation> expectations =
        injectExpectationRepository.rawForComputeGlobalByInjectIds(injectIds);

    return injectExpectationMapper.extractExpectationResultByTypesFromRaw(injectIds, expectations);
  }

  public List<InjectExpectationResultsByAttackPattern> computeInjectExpectationResults(
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
        .map(
            entry ->
                injectExpectationMapper.toInjectExpectationResultsByattackPattern(
                    entry.getKey(), entry.getValue()))
        .toList();
  }
}
