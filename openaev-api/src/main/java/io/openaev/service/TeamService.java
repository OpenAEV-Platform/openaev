package io.openaev.service;

import io.openaev.context.TenantContext;
import io.openaev.database.model.Tag;
import io.openaev.database.model.Team;
import io.openaev.database.model.User;
import io.openaev.database.repository.TeamRepository;
import io.openaev.rest.team.output.TeamOutput;
import io.openaev.utils.CopyObjectListUtils;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TeamService {

  private final EntityManager entityManager;
  private final TeamRepository teamRepository;

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

  // -- LIST --

  /**
   * Paginated team list using native SQL.
   *
   * <p>Returns only scalar fields plus a user COUNT. Use this for search/list endpoints where the
   * UI only needs to display the team name and user count.
   *
   * @param input pagination and optional text search
   * @param contextual when non-null, filters by the team_contextual flag
   * @return paginated list of lightweight team projections
   */
  @Transactional(readOnly = true)
  public Page<TeamOutput> teamPaginationSimple(
      @NotNull SearchPaginationInput input, Boolean contextual) {
    String tenantId = TenantContext.getCurrentTenant();
    String tenantClause = tenantId != null ? "AND t.tenant_id = :tenantId " : "";
    boolean hasSearch = StringUtils.isNotBlank(input.getTextSearch());
    String search = hasSearch ? "%" + input.getTextSearch() + "%" : null;
    int pageSize = input.getSize();
    long offset = (long) input.getPage() * pageSize;
    String contextualClause =
        contextual != null ? "AND t.team_contextual = :contextual " : "";

    String dataSQL =
        """
        SELECT t.team_id, t.team_name, t.team_description, t.team_contextual,
               t.team_updated_at,
               COUNT(DISTINCT tu.user_id)                                          AS team_users_number,
               array_agg(DISTINCT tt.tag_id) FILTER (WHERE tt.tag_id IS NOT NULL)  AS team_tags
        FROM teams t
        LEFT JOIN users_teams tu ON tu.team_id = t.team_id
        LEFT JOIN teams_tags  tt ON tt.team_id = t.team_id
        WHERE 1=1
        """
            + tenantClause
            + contextualClause
            + """
              AND (:hasSearch = false OR t.team_name ILIKE :search)
            GROUP BY t.team_id
            ORDER BY t.team_name ASC
            LIMIT :pageSize OFFSET :offset
            """;

    String countSQL =
        """
        SELECT COUNT(DISTINCT t.team_id)
        FROM teams t
        WHERE 1=1
        """
            + tenantClause
            + contextualClause
            + """
              AND (:hasSearch = false OR t.team_name ILIKE :search)
            """;

    Query dataQuery = entityManager.createNativeQuery(dataSQL);
    Query countQuery = entityManager.createNativeQuery(countSQL);
    for (Query q : List.of(dataQuery, countQuery)) {
      if (tenantId != null) q.setParameter("tenantId", tenantId);
      if (contextual != null) q.setParameter("contextual", contextual);
      q.setParameter("hasSearch", hasSearch);
      q.setParameter("search", search);
    }
    dataQuery.setParameter("pageSize", pageSize);
    dataQuery.setParameter("offset", offset);

    @SuppressWarnings("unchecked")
    List<TeamOutput> teams =
        ((List<Object[]>) dataQuery.getResultList())
            .stream().map(TeamOutput::fromRow).toList();
    long total = ((Number) countQuery.getSingleResult()).longValue();
    return new PageImpl<>(teams, PageRequest.of(input.getPage(), pageSize), total);
  }

  /**
   * Fetch teams by their IDs using native SQL.
   *
   * @param teamIds list of team IDs to fetch
   * @return the found teams as TeamOutput
   */
  @Transactional(readOnly = true)
  public List<TeamOutput> findByIds(@NotNull List<String> teamIds) {
    if (teamIds.isEmpty()) {
      return List.of();
    }
    String tenantId = TenantContext.getCurrentTenant();
    String tenantClause = tenantId != null ? "AND t.tenant_id = :tenantId " : "";
    String sql =
        """
        SELECT t.team_id, t.team_name, t.team_description, t.team_contextual,
               t.team_updated_at,
               COUNT(DISTINCT tu.user_id)                                          AS team_users_number,
               array_agg(DISTINCT tt.tag_id) FILTER (WHERE tt.tag_id IS NOT NULL)  AS team_tags
        FROM teams t
        LEFT JOIN users_teams tu ON tu.team_id = t.team_id
        LEFT JOIN teams_tags  tt ON tt.team_id = t.team_id
        WHERE t.team_id IN (:ids)
        """
            + tenantClause
            + """
            GROUP BY t.team_id
            ORDER BY t.team_name ASC
            """;
    Query q = entityManager.createNativeQuery(sql);
    q.setParameter("ids", teamIds);
    if (tenantId != null) q.setParameter("tenantId", tenantId);
    @SuppressWarnings("unchecked")
    List<TeamOutput> result =
        ((List<Object[]>) q.getResultList()).stream().map(TeamOutput::fromRow).toList();
    return result;
  }

  /**
   * Fetch all teams linked to a simulation using native SQL.
   *
   * @param exerciseId the simulation ID
   * @return the teams of this simulation
   */
  @Transactional(readOnly = true)
  public List<TeamOutput> findByExerciseId(@NotBlank String exerciseId) {
    String tenantId = TenantContext.getCurrentTenant();
    String tenantClause = tenantId != null ? "AND t.tenant_id = :tenantId " : "";
    String sql =
        """
        SELECT t.team_id, t.team_name, t.team_description, t.team_contextual,
               t.team_updated_at,
               COUNT(DISTINCT tu.user_id)                                          AS team_users_number,
               array_agg(DISTINCT tt.tag_id) FILTER (WHERE tt.tag_id IS NOT NULL)  AS team_tags
        FROM teams t
        INNER JOIN exercises_teams et ON et.team_id = t.team_id AND et.exercise_id = :exerciseId
        LEFT  JOIN users_teams tu     ON tu.team_id = t.team_id
        LEFT  JOIN teams_tags  tt     ON tt.team_id = t.team_id
        WHERE 1=1
        """
            + tenantClause
            + """
            GROUP BY t.team_id
            ORDER BY t.team_name ASC
            """;
    Query q = entityManager.createNativeQuery(sql);
    q.setParameter("exerciseId", exerciseId);
    if (tenantId != null) q.setParameter("tenantId", tenantId);
    @SuppressWarnings("unchecked")
    List<TeamOutput> result =
        ((List<Object[]>) q.getResultList()).stream().map(TeamOutput::fromRow).toList();
    return result;
  }

  /**
   * Fetch all teams linked to a scenario using native SQL.
   *
   * @param scenarioId the scenario ID
   * @return the teams of this scenario
   */
  @Transactional(readOnly = true)
  public List<TeamOutput> findByScenarioId(@NotBlank String scenarioId) {
    String tenantId = TenantContext.getCurrentTenant();
    String tenantClause = tenantId != null ? "AND t.tenant_id = :tenantId " : "";
    String sql =
        """
        SELECT t.team_id, t.team_name, t.team_description, t.team_contextual,
               t.team_updated_at,
               COUNT(DISTINCT tu.user_id)                                          AS team_users_number,
               array_agg(DISTINCT tt.tag_id) FILTER (WHERE tt.tag_id IS NOT NULL)  AS team_tags
        FROM teams t
        INNER JOIN scenarios_teams st ON st.team_id = t.team_id AND st.scenario_id = :scenarioId
        LEFT  JOIN users_teams tu     ON tu.team_id = t.team_id
        LEFT  JOIN teams_tags  tt     ON tt.team_id = t.team_id
        WHERE 1=1
        """
            + tenantClause
            + """
            GROUP BY t.team_id
            ORDER BY t.team_name ASC
            """;
    Query q = entityManager.createNativeQuery(sql);
    q.setParameter("scenarioId", scenarioId);
    if (tenantId != null) q.setParameter("tenantId", tenantId);
    @SuppressWarnings("unchecked")
    List<TeamOutput> result =
        ((List<Object[]>) q.getResultList()).stream().map(TeamOutput::fromRow).toList();
    return result;
  }

  /**
   * Fetch teams corresponding to given IDs (full JPA entities).
   *
   * @param teamIds list of team IDs to fetch
   * @return the found teams
   */
  public List<Team> getTeamsByIds(List<String> teamIds) {
    return teamRepository.findAllById(teamIds);
  }

  // -- EXERCISE / SCENARIO TEAM PAGINATION --

  /**
   * Paginated team search scoped to a simulation using native SQL.
   *
   * <p>For {@code contextualOnly=true}: INNER JOIN on {@code exercises_teams} drives the query
   * directly from the exercise_id index — no subquery, no IN list, O(exercise_teams) scan only.
   * For {@code contextualOnly=false}: non-contextual teams UNION exercise teams via EXISTS.
   *
   * @param exerciseId exercise to scope to
   * @param input pagination and optional text search
   * @param contextualOnly true = only teams in this exercise; false = non-contextual + exercise
   * @return paginated lightweight team projections
   */
  @Transactional(readOnly = true)
  public Page<TeamOutput> exerciseTeamPagination(
      @NotBlank String exerciseId,
      @NotNull SearchPaginationInput input,
      boolean contextualOnly) {
    String tenantId = TenantContext.getCurrentTenant();
    // null tenantId = admin user, bypasses tenant isolation (same as Hibernate @Filter behaviour)
    String tenantClause = tenantId != null ? "AND t.tenant_id = :tenantId " : "";
    boolean hasSearch = StringUtils.isNotBlank(input.getTextSearch());
    String search = hasSearch ? "%" + input.getTextSearch() + "%" : null;
    int pageSize = input.getSize();
    long offset = (long) input.getPage() * pageSize;

    String dataSQL;
    String countSQL;

    if (contextualOnly) {
      dataSQL = """
          SELECT t.team_id, t.team_name, t.team_description, t.team_contextual,
                 t.team_updated_at,
                 COUNT(DISTINCT tu.user_id)                                          AS team_users_number,
                 array_agg(DISTINCT tt.tag_id) FILTER (WHERE tt.tag_id IS NOT NULL)  AS team_tags
          FROM teams t
          INNER JOIN exercises_teams et ON et.team_id = t.team_id AND et.exercise_id = :contextId
          LEFT  JOIN users_teams tu     ON tu.team_id = t.team_id
          LEFT  JOIN teams_tags  tt     ON tt.team_id = t.team_id
          WHERE 1=1
          """ + tenantClause + """
            AND (:hasSearch = false OR t.team_name ILIKE :search)
          GROUP BY t.team_id
          ORDER BY t.team_name ASC
          LIMIT :pageSize OFFSET :offset
          """;
      countSQL = """
          SELECT COUNT(DISTINCT t.team_id)
          FROM teams t
          INNER JOIN exercises_teams et ON et.team_id = t.team_id AND et.exercise_id = :contextId
          WHERE 1=1
          """ + tenantClause + """
            AND (:hasSearch = false OR t.team_name ILIKE :search)
          """;
    } else {
      dataSQL = """
          SELECT t.team_id, t.team_name, t.team_description, t.team_contextual,
                 t.team_updated_at,
                 COUNT(DISTINCT tu.user_id)                                          AS team_users_number,
                 array_agg(DISTINCT tt.tag_id) FILTER (WHERE tt.tag_id IS NOT NULL)  AS team_tags
          FROM teams t
          LEFT  JOIN users_teams tu ON tu.team_id = t.team_id
          LEFT  JOIN teams_tags  tt ON tt.team_id = t.team_id
          WHERE (t.team_contextual = false
                 OR EXISTS (SELECT 1 FROM exercises_teams et
                            WHERE et.team_id = t.team_id AND et.exercise_id = :contextId))
          """ + tenantClause + """
            AND (:hasSearch = false OR t.team_name ILIKE :search)
          GROUP BY t.team_id
          ORDER BY t.team_name ASC
          LIMIT :pageSize OFFSET :offset
          """;
      countSQL = """
          SELECT COUNT(DISTINCT t.team_id)
          FROM teams t
          WHERE (t.team_contextual = false
                 OR EXISTS (SELECT 1 FROM exercises_teams et
                            WHERE et.team_id = t.team_id AND et.exercise_id = :contextId))
          """ + tenantClause + """
            AND (:hasSearch = false OR t.team_name ILIKE :search)
          """;
    }

    List<TeamOutput> teams =
        runNativeTeamQuery(dataSQL, exerciseId, tenantId, hasSearch, search, pageSize, offset);
    long total = countNativeTeamQuery(countSQL, exerciseId, tenantId, hasSearch, search);
    return new PageImpl<>(teams, PageRequest.of(input.getPage(), pageSize), total);
  }

  /**
   * Paginated team search scoped to a scenario using native SQL.
   *
   * @see #exerciseTeamPagination for full description — same pattern for scenarios.
   */
  @Transactional(readOnly = true)
  public Page<TeamOutput> scenarioTeamPagination(
      @NotBlank String scenarioId,
      @NotNull SearchPaginationInput input,
      boolean contextualOnly) {
    String tenantId = TenantContext.getCurrentTenant();
    String tenantClause = tenantId != null ? "AND t.tenant_id = :tenantId " : "";
    boolean hasSearch = StringUtils.isNotBlank(input.getTextSearch());
    String search = hasSearch ? "%" + input.getTextSearch() + "%" : null;
    int pageSize = input.getSize();
    long offset = (long) input.getPage() * pageSize;

    String dataSQL;
    String countSQL;

    if (contextualOnly) {
      dataSQL = """
          SELECT t.team_id, t.team_name, t.team_description, t.team_contextual,
                 t.team_updated_at,
                 COUNT(DISTINCT tu.user_id)                                          AS team_users_number,
                 array_agg(DISTINCT tt.tag_id) FILTER (WHERE tt.tag_id IS NOT NULL)  AS team_tags
          FROM teams t
          INNER JOIN scenarios_teams st ON st.team_id = t.team_id AND st.scenario_id = :contextId
          LEFT  JOIN users_teams tu     ON tu.team_id = t.team_id
          LEFT  JOIN teams_tags  tt     ON tt.team_id = t.team_id
          WHERE 1=1
          """ + tenantClause + """
            AND (:hasSearch = false OR t.team_name ILIKE :search)
          GROUP BY t.team_id
          ORDER BY t.team_name ASC
          LIMIT :pageSize OFFSET :offset
          """;
      countSQL = """
          SELECT COUNT(DISTINCT t.team_id)
          FROM teams t
          INNER JOIN scenarios_teams st ON st.team_id = t.team_id AND st.scenario_id = :contextId
          WHERE 1=1
          """ + tenantClause + """
            AND (:hasSearch = false OR t.team_name ILIKE :search)
          """;
    } else {
      dataSQL = """
          SELECT t.team_id, t.team_name, t.team_description, t.team_contextual,
                 t.team_updated_at,
                 COUNT(DISTINCT tu.user_id)                                          AS team_users_number,
                 array_agg(DISTINCT tt.tag_id) FILTER (WHERE tt.tag_id IS NOT NULL)  AS team_tags
          FROM teams t
          LEFT  JOIN users_teams tu ON tu.team_id = t.team_id
          LEFT  JOIN teams_tags  tt ON tt.team_id = t.team_id
          WHERE (t.team_contextual = false
                 OR EXISTS (SELECT 1 FROM scenarios_teams st
                            WHERE st.team_id = t.team_id AND st.scenario_id = :contextId))
          """ + tenantClause + """
            AND (:hasSearch = false OR t.team_name ILIKE :search)
          GROUP BY t.team_id
          ORDER BY t.team_name ASC
          LIMIT :pageSize OFFSET :offset
          """;
      countSQL = """
          SELECT COUNT(DISTINCT t.team_id)
          FROM teams t
          WHERE (t.team_contextual = false
                 OR EXISTS (SELECT 1 FROM scenarios_teams st
                            WHERE st.team_id = t.team_id AND st.scenario_id = :contextId))
          """ + tenantClause + """
            AND (:hasSearch = false OR t.team_name ILIKE :search)
          """;
    }

    List<TeamOutput> teams =
        runNativeTeamQuery(dataSQL, scenarioId, tenantId, hasSearch, search, pageSize, offset);
    long total = countNativeTeamQuery(countSQL, scenarioId, tenantId, hasSearch, search);
    return new PageImpl<>(teams, PageRequest.of(input.getPage(), pageSize), total);
  }

  // -- NATIVE QUERY HELPERS --

  @SuppressWarnings("unchecked")
  private List<TeamOutput> runNativeTeamQuery(
      String sql,
      String contextId,
      String tenantId,
      boolean hasSearch,
      String search,
      int pageSize,
      long offset) {
    Query q = entityManager.createNativeQuery(sql);
    q.setParameter("contextId", contextId);
    if (tenantId != null) q.setParameter("tenantId", tenantId);
    q.setParameter("hasSearch", hasSearch);
    q.setParameter("search", search);
    q.setParameter("pageSize", pageSize);
    q.setParameter("offset", offset);
    return ((List<Object[]>) q.getResultList()).stream().map(TeamOutput::fromRow).toList();
  }

  private long countNativeTeamQuery(
      String sql, String contextId, String tenantId, boolean hasSearch, String search) {
    Query q = entityManager.createNativeQuery(sql);
    q.setParameter("contextId", contextId);
    if (tenantId != null) q.setParameter("tenantId", tenantId);
    q.setParameter("hasSearch", hasSearch);
    q.setParameter("search", search);
    return ((Number) q.getSingleResult()).longValue();
  }
}
