package io.openaev.service;

import static io.openaev.database.criteria.GenericCriteria.countQuery;
import static io.openaev.rest.team.TeamQueryHelper.execution;
import static io.openaev.rest.team.TeamQueryHelper.select;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationCriteriaBuilder;
import static io.openaev.utils.pagination.SortUtilsCriteriaBuilder.toSortCriteriaBuilder;

import io.openaev.database.model.Tag;
import io.openaev.database.model.Team;
import io.openaev.database.model.User;
import io.openaev.database.raw.RawTeam;
import io.openaev.database.repository.TeamRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.team.output.TeamOutput;
import io.openaev.utils.CopyObjectListUtils;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.function.TriFunction;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TeamService {

  @PersistenceContext private EntityManager entityManager;

  private final UserRepository userRepository;
  private final TeamRepository teamRepository;

  private final UserService userService;

  public List<TeamOutput> getTeams(@NotNull List<String> teamIds) {
    List<RawTeam> rawTeams =
        teamRepository.rawTeamByIds(teamIds).stream()
            .sorted(Comparator.comparing(RawTeam::getTeam_name))
            .toList();
    return rawTeams.stream()
        .map(rt -> TeamOutput.builder().id(rt.getTeam_id()).name(rt.getTeam_name()).build())
        .toList();
  }

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

  public List<TeamOutput> find(Specification<Team> specification) {
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

    TypedQuery<Tuple> query = entityManager.createQuery(cq);
    return execution(query);
  }
}
