package io.openbas.rest.statistic;

import static io.openbas.config.SessionHelper.currentUser;
import static io.openbas.helper.StreamHelper.fromIterable;
import static io.openbas.utils.InjectExpectationResultUtils.getExpectationResultByTypes;
import static java.util.stream.Collectors.groupingBy;

import io.openbas.aop.LogExecutionTime;
import io.openbas.aop.RBAC;
import io.openbas.config.OpenBASPrincipal;
import io.openbas.database.model.AttackPattern;
import io.openbas.database.raw.RawGlobalInjectExpectation;
import io.openbas.database.raw.RawInjectExpectation;
import io.openbas.database.raw.impl.SimpleRawInjectExpectation;
import io.openbas.database.repository.*;
import io.openbas.rest.helper.RestBehavior;
import io.openbas.rest.inject.form.InjectExpectationResultsByAttackPattern;
import io.openbas.rest.statistic.response.PlatformStatistic;
import io.openbas.rest.statistic.response.StatisticElement;
import io.openbas.service.UserService;
import io.openbas.utils.InjectExpectationResultUtils;
import io.openbas.utils.InjectExpectationResultUtils.ExpectationResultsByType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

enum Type {
  GLOBAL,
  USER,
}

@RestController
@RequiredArgsConstructor
public class StatisticApi extends RestBehavior {

  private final ScenarioRepository scenarioRepository;
  private final ExerciseRepository exerciseRepository;
  private final UserRepository userRepository;
  private final TeamRepository teamRepository;
  private final EndpointRepository endpointRepository;
  private final AssetGroupRepository assetGroupRepository;
  private final InjectRepository injectRepository;
  private final UserService userService;

  @LogExecutionTime
  @GetMapping("/api/statistics")
  // FIXME open this api is necessary for login but that should not be the case
  @RBAC(skipRBAC = true)
  @Transactional(rollbackOn = Exception.class)
  @Operation(summary = "Retrieve platform statistics")
  @ApiResponse(
      responseCode = "200",
      description = "Successful operation",
      content = {
        @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = PlatformStatistic.class))
      })
  public PlatformStatistic platformStatistic() {
    Instant now = Instant.now();
    PlatformStatistic statistic = new PlatformStatistic();
    if (userService.currentUser().isAdminOrBypass()) {
      statistic.setScenariosCount(computeGlobalStat(now, scenarioRepository));
      statistic.setExercisesCount(computeGlobalStat(now, exerciseRepository));
      statistic.setUsersCount(computeGlobalStat(now, userRepository));
      statistic.setTeamsCount(computeGlobalStat(now, teamRepository));
      statistic.setAssetsCount(computeGlobalStat(now, endpointRepository));
      statistic.setAssetGroupsCount(computeGlobalStat(now, assetGroupRepository));
      statistic.setInjectsCount(computeGlobalStat(now, injectRepository));
      statistic.setResults(computeGlobalExpectationResults(now));
      statistic.setInjectResults(computeGlobalInjectExpectationResults(now));
      statistic.setExerciseCountByCategory(computeExerciseCountGroupByCategory(Type.GLOBAL, now));
      statistic.setExercisesCountByWeek(computeExerciseCountGroupByWeek(Type.GLOBAL, now));
      statistic.setInjectsCountByAttackPattern(
          computeInjectCountGroupByAttackPattern(Type.GLOBAL, now));
    } else {
      statistic.setScenariosCount(computeUserStat(now, scenarioRepository));
      statistic.setExercisesCount(computeUserStat(now, exerciseRepository));
      statistic.setUsersCount(computeUserStat(now, userRepository));
      statistic.setTeamsCount(computeUserStat(now, teamRepository));
      statistic.setAssetsCount(computeUserStat(now, endpointRepository));
      statistic.setAssetGroupsCount(computeUserStat(now, assetGroupRepository));
      statistic.setInjectsCount(computeUserStat(now, injectRepository));
      statistic.setResults(computeUserExpectationResults(now));
      statistic.setInjectResults(computeUserInjectExpectationResults(now));
      statistic.setExerciseCountByCategory(computeExerciseCountGroupByCategory(Type.USER, now));
      statistic.setExercisesCountByWeek(computeExerciseCountGroupByWeek(Type.USER, now));
      statistic.setInjectsCountByAttackPattern(
          computeInjectCountGroupByAttackPattern(Type.USER, now));
    }
    return statistic;
  }

  // -- GLOBAL STATISTIC --

  private StatisticElement computeGlobalStat(Instant from, StatisticRepository repository) {
    Instant minus6Months = from.minus(180, ChronoUnit.DAYS);
    long global = repository.globalCount(minus6Months);
    Instant minusMonth = from.minus(30, ChronoUnit.DAYS);
    long progression = repository.globalCount(minusMonth);
    return new StatisticElement(global, progression);
  }

  private StatisticElement computeUserStat(Instant from, StatisticRepository repository) {
    OpenBASPrincipal user = currentUser();
    Instant minus6Months = from.minus(180, ChronoUnit.DAYS);
    long global = repository.userCount(user.getId(), minus6Months);
    Instant minusMonth = from.minus(30, ChronoUnit.DAYS);
    long progression = repository.userCount(user.getId(), minusMonth);
    return new StatisticElement(global, progression);
  }

  private List<ExpectationResultsByType> computeGlobalExpectationResults(
      @NotNull final Instant from) {
    Instant minus6Months = from.minus(180, ChronoUnit.DAYS);
    List<RawInjectExpectation> rawInjectExpectations =
        fromIterable(this.exerciseRepository.allInjectExpectationsFromDate(minus6Months));
    return getExpectationResultByTypes(
        rawInjectExpectations, InjectExpectationResultUtils::getScoresFromRaw);
  }

  private List<ExpectationResultsByType> computeUserExpectationResults(
      @NotNull final Instant from) {
    OpenBASPrincipal user = currentUser();
    Instant minus6Months = from.minus(180, ChronoUnit.DAYS);
    List<RawInjectExpectation> rawInjectExpectations =
        fromIterable(
            this.exerciseRepository.allGrantedInjectExpectationsFromDate(
                minus6Months, user.getId()));
    return getExpectationResultByTypes(
        rawInjectExpectations, InjectExpectationResultUtils::getScoresFromRaw);
  }

  private List<InjectExpectationResultsByAttackPattern> computeGlobalInjectExpectationResults(
      @NotNull final Instant from) {
    Instant minus6Months = from.minus(180, ChronoUnit.DAYS);
    List<RawGlobalInjectExpectation> rawGlobalInjectExpectations =
        fromIterable(
            this.exerciseRepository.rawGlobalInjectExpectationResultsFromDate(minus6Months));
    return injectExpectationResultsByAttackPatternFromRawGlobalInjectExpectation(
        rawGlobalInjectExpectations);
  }

  private List<InjectExpectationResultsByAttackPattern> computeUserInjectExpectationResults(
      @NotNull final Instant from) {
    OpenBASPrincipal user = currentUser();
    Instant minus6Months = from.minus(180, ChronoUnit.DAYS);
    List<RawGlobalInjectExpectation> rawGlobalInjectExpectations =
        fromIterable(
            this.exerciseRepository.rawGrantedInjectExpectationResultsFromDate(
                minus6Months, user.getId()));
    return injectExpectationResultsByAttackPatternFromRawGlobalInjectExpectation(
        rawGlobalInjectExpectations);
  }

  private List<InjectExpectationResultsByAttackPattern>
      injectExpectationResultsByAttackPatternFromRawGlobalInjectExpectation(
          List<RawGlobalInjectExpectation> rawGlobalInjectExpectations) {
    return rawGlobalInjectExpectations.stream()
        .map(RawGlobalInjectExpectation::getAttack_pattern_id)
        .distinct()
        .map(
            attackPatternId -> {
              InjectExpectationResultsByAttackPattern resultExpectation =
                  new InjectExpectationResultsByAttackPattern();
              resultExpectation.setAttackPattern(new AttackPattern());
              resultExpectation.getAttackPattern().setId(attackPatternId);

              Map<String, Map<String, List<RawGlobalInjectExpectation>>>
                  rawGlobalInjectExpectationsGroupByAttackAndInjectId =
                      rawGlobalInjectExpectations.stream()
                          .collect(
                              groupingBy(
                                  RawGlobalInjectExpectation::getAttack_pattern_id,
                                  groupingBy(RawGlobalInjectExpectation::getInject_id)));

              List<InjectExpectationResultsByAttackPattern.InjectExpectationResultsByType> results =
                  new ArrayList<>();

              rawGlobalInjectExpectationsGroupByAttackAndInjectId.forEach(
                  (attackId, injects) -> {
                    if (attackId.equals(attackPatternId)) {
                      injects.forEach(
                          (injectId, expectations) -> {
                            RawGlobalInjectExpectation expectation = expectations.getFirst();
                            InjectExpectationResultsByAttackPattern.InjectExpectationResultsByType
                                resultInjectExpectationResultsByAttackPattern =
                                    new InjectExpectationResultsByAttackPattern
                                        .InjectExpectationResultsByType();
                            resultInjectExpectationResultsByAttackPattern.setInjectTitle(
                                expectation.getInject_title());

                            ArrayList<RawInjectExpectation> expectationsRefined = new ArrayList<>();

                            expectations.stream()
                                .forEach(
                                    e -> {
                                      if (e.getInject_expectation_type() != null) {
                                        SimpleRawInjectExpectation rawInjectExpectation =
                                            new SimpleRawInjectExpectation();
                                        rawInjectExpectation.setInject_expectation_score(
                                            e.getInject_expectation_score());
                                        rawInjectExpectation.setInject_expectation_expected_score(
                                            e.getInject_expectation_expected_score());
                                        rawInjectExpectation.setInject_expectation_type(
                                            e.getInject_expectation_type());
                                        expectationsRefined.add(rawInjectExpectation);
                                      }
                                    });

                            resultInjectExpectationResultsByAttackPattern.setResults(
                                getExpectationResultByTypes(
                                    expectationsRefined,
                                    InjectExpectationResultUtils::getScoresFromRaw));

                            results.add(resultInjectExpectationResultsByAttackPattern);
                          });
                    }
                  });

              resultExpectation.setResults(results);

              return resultExpectation;
            })
        .collect(Collectors.toList());
  }

  private Map<String, Long> computeExerciseCountGroupByCategory(
      final Type type, @NotNull final Instant from) {
    Instant minus6Months = from.minus(180, ChronoUnit.DAYS);
    List<Object[]> result = new ArrayList<>();
    if (type == Type.GLOBAL) {
      result = exerciseRepository.globalCountGroupByCategory(minus6Months);
    } else if (type == Type.USER) {
      OpenBASPrincipal user = currentUser();
      result = exerciseRepository.userCountGroupByCategory(user.getId(), minus6Months);
    }
    Map<String, Long> categoryCountMap = new HashMap<>();
    for (Object[] row : result) {
      String category = (String) row[0];
      Long count = (Long) row[1];
      categoryCountMap.put(category, count);
    }
    return categoryCountMap;
  }

  private Map<Instant, Long> computeExerciseCountGroupByWeek(
      final Type type, @NotNull final Instant from) {
    Instant minus6Months = from.minus(180, ChronoUnit.DAYS);
    List<Object[]> result = new ArrayList<>();
    if (type == Type.GLOBAL) {
      result = exerciseRepository.globalCountGroupByWeek(minus6Months);
    } else if (type == Type.USER) {
      OpenBASPrincipal user = currentUser();
      result = exerciseRepository.userCountGroupByWeek(user.getId(), minus6Months);
    }
    Map<Instant, Long> weekCountMap = new HashMap<>();
    for (Object[] row : result) {
      Instant week = (Instant) row[0];
      Long count = (Long) row[1];
      weekCountMap.put(week, count);
    }
    return weekCountMap;
  }

  private Map<String, Long> computeInjectCountGroupByAttackPattern(
      final Type type, @NotNull final Instant from) {
    Instant minus6Months = from.minus(180, ChronoUnit.DAYS);
    List<Object[]> result = new ArrayList<>();
    if (type == Type.GLOBAL) {
      result = injectRepository.globalCountGroupByAttackPatternInExercise(minus6Months);
    } else if (type == Type.USER) {
      OpenBASPrincipal user = currentUser();
      result = injectRepository.userCountGroupByAttackPatternInExercise(user.getId(), minus6Months);
    }
    Map<String, Long> attackPatternMap = new HashMap<>();
    for (Object[] row : result) {
      String attackPattern = (String) row[0];
      Long count = (Long) row[1];
      attackPatternMap.put(attackPattern, count);
    }
    return attackPatternMap;
  }
}
