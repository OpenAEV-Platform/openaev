package io.openaev.service.targets.search;

import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.openaev.database.model.*;
import io.openaev.database.repository.TeamRepository;
import io.openaev.service.TeamService;
import io.openaev.utils.FilterUtilsJpa;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.persistence.criteria.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class TeamTargetSearchAdaptor extends SearchAdaptorBase {

  private final TeamService teamService;
  private final TeamRepository teamRepository;
  private final HelperTargetSearchAdaptor helperTargetSearchAdaptor;

  public TeamTargetSearchAdaptor(
      TeamService teamService,
      TeamRepository teamRepository,
      HelperTargetSearchAdaptor helperTargetSearchAdaptor) {
    this.teamService = teamService;
    this.teamRepository = teamRepository;
    this.helperTargetSearchAdaptor = helperTargetSearchAdaptor;

    // field name translations
    this.fieldTranslations.put("target_name", "team_name");
    this.fieldTranslations.put("target_tags", "team_tags");
  }

  private static Specification<Team> teamsSpecificationFromInject(Inject scopedInject) {
    return (root, query, builder) -> {
      if (scopedInject.isAtomicTesting()) {
        Path<Object> injectPath = root.join("injects").get("id");
        return builder.equal(injectPath, scopedInject.getId());
      } else {
        if (scopedInject.isAllTeams()) {
          Path<Object> exerciseTeamUsersPath =
              root.get("exerciseTeamUsers").get("exercise").get("id");
          Path<Object> injectPath = root.join("exercises").get("injects").get("id");
          return builder.and(
              builder.equal(injectPath, scopedInject.getId()),
              builder.equal(exerciseTeamUsersPath, scopedInject.getExercise().getId()));
        } else {
          Path<Object> exerciseTeamUsersPath =
              root.get("exerciseTeamUsers").get("exercise").get("id");
          Path<Object> injectPath = root.join("injects").get("id");
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

    Page<Team> filteredTeams =
        buildPaginationJPA(
            (Specification<Team> specification, Pageable pageable) ->
                this.teamRepository.findAll(
                    teamsSpecificationFromInject(scopedInject).and(specification), pageable),
            translatedInput,
            Team.class);

    return new PageImpl<>(
        filteredTeams.getContent().stream()
            .map(team -> convertFromTeamOutput(team, scopedInject))
            .toList(),
        filteredTeams.getPageable(),
        filteredTeams.getTotalElements());
  }

  @Override
  public List<FilterUtilsJpa.Option> getOptionsForInject(Inject scopedInject, String textSearch) {
    if (scopedInject.isAllTeams()) {
      return scopedInject.getExercise().getTeams().stream()
          .filter(team -> team.getName().toLowerCase().contains(textSearch.toLowerCase()))
          .map(team -> new FilterUtilsJpa.Option(team.getId(), team.getName()))
          .toList();
    } else {
      return scopedInject.getTeams().stream()
          .filter(team -> team.getName().toLowerCase().contains(textSearch.toLowerCase()))
          .map(team -> new FilterUtilsJpa.Option(team.getId(), team.getName()))
          .toList();
    }
  }

  @Override
  public List<FilterUtilsJpa.Option> getOptionsByIds(List<String> ids) {
    return teamService.getTeams(ids).stream()
        .map(team -> new FilterUtilsJpa.Option(team.getId(), team.getName()))
        .toList();
  }

  private InjectTarget convertFromTeamOutput(Team team, Inject inject) {
    return helperTargetSearchAdaptor.buildTargetWithExpectations(
        inject,
        () ->
            new TeamTarget(
                team.getId(),
                team.getName(),
                team.getTags().stream().map(Tag::getId).collect(Collectors.toSet())),
        false);
  }
}
