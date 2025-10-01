package io.openaev.utils;

import static java.util.Collections.emptyList;

import io.openaev.database.model.*;
import io.openaev.database.raw.RawInjectExpectation;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.rest.inject.form.InjectExpectationResultsByAttackPattern;
import io.openaev.utils.InjectExpectationResultUtils.ExpectationResultsByType;
import io.openaev.utils.mapper.InjectExpectationMapper;
import jakarta.validation.constraints.NotNull;
import java.util.Comparator;
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

  public List<ExpectationResultsByType> computeGlobalExpectationResults(Set<String> injectIds) {

    if (injectIds == null || injectIds.isEmpty()) {
      return emptyList();
    }

    List<RawInjectExpectation> expectations =
        injectExpectationRepository.rawForComputeGlobalByInjectIds(injectIds);

    return injectExpectationMapper.extractExpectationResultByTypesFromRaw(injectIds, expectations);
  }

  public List<ExpectationResultsByType> computeGlobalExpectationResultsForPlatform(
      Set<String> injectIds, SecurityPlatform securityPlatform) {

    if (injectIds == null || injectIds.isEmpty()) {
      return emptyList();
    }

    List<InjectExpectation> expectations =
        injectExpectationRepository.findAllForGlobalScoreByInjects(injectIds).stream()
            .map(InjectExpectation::clone)
            .toList();
    expectations.forEach(
        exp -> {
          exp.setResults(
              exp.getResults().stream()
                  .filter(r -> r.getSourceId().equals(securityPlatform.getId()))
                  .toList());

          exp.setScore(
              exp.getResults().stream()
                  .max(Comparator.comparing(InjectExpectationResult::getScore))
                  .map(InjectExpectationResult::getScore)
                  .orElse(null));
        });

    return injectExpectationMapper.extractExpectationResultByTypes(injectIds, expectations);
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
