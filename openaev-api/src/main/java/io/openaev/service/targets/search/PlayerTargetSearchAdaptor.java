package io.openaev.service.targets.search;

import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.openaev.database.model.*;
import io.openaev.database.repository.UserRepository;
import io.openaev.utils.FilterUtilsJpa;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.openaev.utils.pagination.SortField;
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
  private final HelperTargetSearchAdaptor helperTargetSearchAdaptor;

  public PlayerTargetSearchAdaptor(
      UserRepository userRepository, HelperTargetSearchAdaptor helperTargetSearchAdaptor) {
    this.userRepository = userRepository;
    this.helperTargetSearchAdaptor = helperTargetSearchAdaptor;

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
    return helperTargetSearchAdaptor.buildTargetWithExpectations(
        inject,
        () ->
            new PlayerTarget(
                player.getId(),
                player.getNameOrEmail(),
                player.getTags().stream().map(Tag::getId).collect(Collectors.toSet()),
                player.getTeams().stream().map(Team::getId).collect(Collectors.toSet())),
        false);
  }
}
