package io.openaev.rest.team;

import static io.openaev.database.specification.TeamSpecification.contextual;
import static io.openaev.rest.atomic_testing.AtomicTestingApi.ATOMIC_TESTING_URI;

import io.openaev.aop.RBAC;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.Team;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.team.output.TeamOutput;
import io.openaev.service.TeamService;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class AtomicTestingTeamApi extends RestBehavior {

  private final TeamService teamService;

  @PostMapping(ATOMIC_TESTING_URI + "/teams/search")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.INJECT)
  @Transactional(readOnly = true)
  public Page<TeamOutput> searchTeams(
      @RequestBody @Valid SearchPaginationInput searchPaginationInput) {
    final Specification<Team> teamSpecification = contextual(false);
    return this.teamService.teamPagination(searchPaginationInput, teamSpecification);
  }
}
