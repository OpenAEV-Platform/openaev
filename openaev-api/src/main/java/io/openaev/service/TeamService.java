package io.openaev.service;

import static io.openaev.database.criteria.GenericCriteria.countQuery;
import static io.openaev.rest.team.TeamQueryHelper.execution;
import static io.openaev.rest.team.TeamQueryHelper.select;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationCriteriaBuilder;
import static io.openaev.utils.pagination.SortUtilsCriteriaBuilder.toSortCriteriaBuilder;

import io.openaev.database.model.Tag;
import io.openaev.database.model.Team;
import io.openaev.database.model.User;
import io.openaev.database.raw.RawTeamIndexing;
import io.openaev.database.repository.TeamRepository;
import io.openaev.rest.team.output.TeamOutput;
import io.openaev.utils.CopyObjectListUtils;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import jakarta.validation.constraints.NotNull;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.function.TriFunction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TeamService {

  private final EntityManager entityManager;
  private final TeamRepository teamRepository;

  /**
   * Fetch teams corresponding to given IDs
   *
   * @param teamIds list of team IDs to fetch
   * @return the found teams as TeamOutput
   */
  public List<TeamOutput> getTeams(@NotNull List<String> teamIds) {
    List<RawTeamIndexing> rawTeams =
        teamRepository.rawTeamByIds(teamIds).stream()
            .sorted(Comparator.comparing(RawTeamIndexing::getTeam_name))
            .toList();
    return rawTeams.stream()
        .map(rt -> TeamOutput.builder().id(rt.getTeam_id()).name(rt.getTeam_name()).build())
        .toList();
  }

  /**
   * Duplicate a contextual team
   *
   * @param teamToCopy the team to copy
   * @return the copied team, not persisted
   */
  public Team copyContextualTeam(Team teamToCopy) {
    Team newTeam = new Team();
    newTeam.setName(teamToCopy.getName());
    newTeam.setDescription(teamToCopy.getDescription());
    newTeam.setTags(CopyObjectListUtils.copy(teamToCopy.getTags(), Tag.class));
    newTeam.setOrganization(teamToCopy.getOrganization());
    newTeam.setUsers(CopyObjectListUtils.copy(teamToCopy.getUsers(), User.class));
    newTeam.setContextual(teamToCopy.getContextual());
    return newTeam;
  }

  /**
   * Fetch a list of teams with a paginated result
   *
   * @param searchPaginationInput pagination criteria
   * @param teamSpecification team search criteria
   * @return list of found teams
   */
  public Page<TeamOutput> teamPagination(
      @NotNull SearchPaginationInput searchPaginationInput,
      @NotNull final Specification<Team> teamSpecification) {
    TriFunction<Specification<Team>, Specification<Team>, Pageable, Page<TeamOutput>> teamsFunction;

    teamsFunction =
        (Specification<Team> specification,
            Specification<Team> specificationCount,
            Pageable pageable) ->
            this.paginate(
                teamSpecification.and(specification),
                teamSpecification.and(specificationCount),
                pageable);

    return buildPaginationCriteriaBuilder(teamsFunction, searchPaginationInput, Team.class);
  }

  /**
   * Generate the page result for a pagination search
   *
   * @param specification criteria for the team search with pagination
   * @param specificationCount criteria for the count of the whole corresponding search, without
   *     pagination
   * @param pageable JPA pageable criteria
   * @return page of matching teams plus total count of corresponding teams
   */
  private Page<TeamOutput> paginate(
      Specification<Team> specification,
      Specification<Team> specificationCount,
      Pageable pageable) {
    CriteriaBuilder cb = this.entityManager.getCriteriaBuilder();

    CriteriaQuery<Tuple> cq = cb.createTupleQuery();
    Root<Team> teamRoot = cq.from(Team.class);
    select(cb, cq, teamRoot);

    // -- Specification --
    if (specification != null) {
      Predicate predicate = specification.toPredicate(teamRoot, cq, cb);
      if (predicate != null) {
        cq.where(predicate);
      }
    }

    // -- Sorting --
    List<Order> orders = toSortCriteriaBuilder(cb, teamRoot, pageable.getSort());
    cq.orderBy(orders);

    // Type Query
    TypedQuery<Tuple> query = entityManager.createQuery(cq);

    // -- Pagination --
    query.setFirstResult((int) pageable.getOffset());
    query.setMaxResults(pageable.getPageSize());

    // -- EXECUTION --
    List<TeamOutput> teams = execution(query);

    // -- Count Query --
    Long total = countQuery(cb, this.entityManager, Team.class, specificationCount);

    return new PageImpl<>(teams, pageable, total);
  }

  /**
   * Fetch a list of teams matching some criteria
   *
   * @param specification criteria to fetch matching teams
   * @return teams found, as TeamOutput
   */
  public List<TeamOutput> find(Specification<Team> specification) {
    CriteriaQuery<Tuple> cq = getTupleCriteriaQuery(specification);
    TypedQuery<Tuple> query = entityManager.createQuery(cq);
    return execution(query);
  }

  /**
   * Generate a criteria builder from a specification
   *
   * @param specification criteria for team search
   * @return the built criteria builder
   */
  private CriteriaQuery<Tuple> getTupleCriteriaQuery(Specification<Team> specification) {
    CriteriaBuilder cb = this.entityManager.getCriteriaBuilder();

    CriteriaQuery<Tuple> cq = cb.createTupleQuery();
    Root<Team> teamRoot = cq.from(Team.class);
    select(cb, cq, teamRoot);

    if (specification != null) {
      Predicate predicate = specification.toPredicate(teamRoot, cq, cb);
      if (predicate != null) {
        cq.where(predicate);
      }
    }
    return cq;
  }

  /**
   * Fetch teams corresponding to given IDs
   *
   * @param teamIds list of team IDs to fetch
   * @return the found teams
   */
  public List<Team> getTeamsByIds(List<String> teamIds) {
    return teamRepository.findAllById(teamIds);
  }
}
