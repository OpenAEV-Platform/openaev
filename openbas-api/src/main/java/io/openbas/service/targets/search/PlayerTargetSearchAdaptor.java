package io.openbas.service.targets.search;

import static io.openbas.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.openbas.database.model.*;
import io.openbas.database.repository.UserRepository;
import io.openbas.service.InjectExpectationService;
import io.openbas.utils.AtomicTestingUtils;
import io.openbas.utils.FilterUtilsJpa;
import io.openbas.utils.pagination.SearchPaginationInput;
import io.openbas.utils.pagination.SortField;
import jakarta.persistence.criteria.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class PlayerTargetSearchAdaptor extends SearchAdaptorBase {
  private final UserRepository userRepository;
  private final InjectExpectationService injectExpectationService;

  public PlayerTargetSearchAdaptor(
      UserRepository userRepository, InjectExpectationService injectExpectationService) {
    this.userRepository = userRepository;
    this.injectExpectationService = injectExpectationService;

    // field name translations
    this.fieldTranslations.put("target_tags", "user_tags");
    this.fieldTranslations.put("target_teams", "user_teams");
  }

  private static Specification<User> playersSpecificationFromInject(Inject scopedInject) {
    return (root, query, builder) -> {
      if (scopedInject.isAtomicTesting()) {
        Path<Object> injectPath = root.join("teams").join("injects").get("id");
        return builder.equal(injectPath, scopedInject.getId());
      } else {
        if (scopedInject.isAllTeams()) {
          Path<Object> exerciseTeamUsersPath =
              root.get("exerciseTeamUsers").get("exercise").get("id");
          Path<Object> injectPath = root.join("teams").join("exercises").get("injects").get("id");
          return builder.and(
              builder.equal(injectPath, scopedInject.getId()),
              builder.equal(exerciseTeamUsersPath, scopedInject.getExercise().getId()));
        } else {
          Path<Object> exerciseTeamUsersPath =
              root.get("exerciseTeamUsers").get("exercise").get("id");
          Path<Object> injectPath = root.join("teams").join("injects").get("id");
          return builder.and(
              builder.equal(injectPath, scopedInject.getId()),
              builder.equal(exerciseTeamUsersPath, scopedInject.getExercise().getId()));
        }
      }
    };
  }

  @Override
  public Page<InjectTarget> search(SearchPaginationInput input, Inject scopedInject) {
    SearchPaginationInput translatedInput = this.translate(input, scopedInject);

    // mind the specific sorts "email" because no name for players
    SortField defaultSort = new SortField("user_email", "ASC");
    translatedInput.setSorts(List.of(defaultSort));

    Page<User> filteredPlayers =
        buildPaginationJPA(
            (Specification<User> specification, Pageable pageable) ->
                this.userRepository.findAll(
                    playersSpecificationFromInject(scopedInject).and(specification), pageable),
            translatedInput,
            User.class);

    return new PageImpl<>(
        filteredPlayers.getContent().stream()
            .map(player -> convertFromPlayerOutput(player, scopedInject))
            .toList(),
        filteredPlayers.getPageable(),
        filteredPlayers.getTotalElements());
  }

  @Override
  public List<FilterUtilsJpa.Option> getOptionsForInject(Inject scopedInject, String textSearch) {
    throw new NotImplementedException("Implement when needed");
  }

  @Override
  public List<FilterUtilsJpa.Option> getOptionsByIds(List<String> ids) {
    throw new NotImplementedException("Implement when needed");
  }

  private InjectTarget convertFromPlayerOutput(User player, Inject inject) {
    PlayerTarget target =
        new PlayerTarget(
            player.getId(),
            player.getNameOrEmail(),
            player.getTags().stream().map(Tag::getId).collect(Collectors.toSet()),
            player.getTeams().stream().map(Team::getId).collect(Collectors.toSet()));

    List<AtomicTestingUtils.ExpectationResultsByType> results =
        AtomicTestingUtils.getExpectationResultByTypes(
            injectExpectationService.findMergedExpectationsByInjectAndTargetAndTargetType(
                inject.getId(), target.getId(), target.getTargetType()));

    for (AtomicTestingUtils.ExpectationResultsByType result : results) {
      switch (result.type()) {
        case DETECTION -> target.setTargetDetectionStatus(result.avgResult());
        case PREVENTION -> target.setTargetPreventionStatus(result.avgResult());
        case HUMAN_RESPONSE -> target.setTargetHumanResponseStatus(result.avgResult());
      }
    }

    return target;
  }
}
