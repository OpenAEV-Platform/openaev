package io.openaev.database.repository;

import io.openaev.database.model.Organization;
import io.openaev.database.raw.RawOrganization;
import jakarta.validation.constraints.NotNull;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OrganizationRepository
    extends CrudRepository<Organization, String>, JpaSpecificationExecutor<Organization> {

  @NotNull
  List<Organization> findByNameIgnoreCaseAndTenantId(
      @NotNull final String name, @NotNull final String tenantId);

  @NotNull
  List<Organization> findAllByIdInAndTenantId(
      @NotNull final Collection<String> ids, @NotNull final String tenantId);

  /**
   * Loads the tags of the given organizations inside the scoped transaction. The organization
   * endpoints return the raw entity, serialized open-in-view after the commit: a lazy {@code tags}
   * load at that point fails closed to an empty array once {@code tags} is v2-active. Run it in the
   * same session as the query that loaded the organizations: Hibernate fills the tags of the
   * entities it already manages.
   */
  @Query("select distinct o from Organization o left join fetch o.tags where o.id in :ids")
  @NotNull
  List<Organization> findAllByIdInFetchingTags(@Param("ids") @NotNull Collection<String> ids);

  /**
   * Lists the organizations of the tenants with their tags and the injects reaching their members:
   * the injects targeting a team of a member, and the "all teams" injects of the simulations and
   * scenarios such a team takes part in, as {@code Team} computes them.
   *
   * <p>Tags and injects are aggregated apart, before the join to the organizations, and the three
   * inject sources are a {@code UNION} of index-backed joins. Joining the injects to the teams with
   * {@code OR inject_all_teams} matched every "all teams" inject of the tenant for every
   * organization, members or not, and multiplied the rows by those of the tags and teams until the
   * list took over a minute (#8926).
   */
  // Native: CTEs, UNION and array_agg build the read-only projection; nothing is written, so no
  // ORM listener side effect is lost.
  @Query(
      value =
          "WITH member_teams AS ("
              + " SELECT DISTINCT o.organization_id, o.tenant_id, t.team_id"
              + " FROM organizations o"
              + " JOIN users_organizations uo ON uo.organization_id = o.organization_id"
              + " JOIN users_teams ut ON ut.user_id = uo.user_id"
              + " JOIN teams t ON t.team_id = ut.team_id AND t.tenant_id = o.tenant_id"
              + " WHERE o.tenant_id IN (:tenantIds)"
              + "), organization_injects AS ("
              + " SELECT mt.organization_id, i.inject_id"
              + " FROM member_teams mt"
              + " JOIN injects_teams it ON it.team_id = mt.team_id"
              + " JOIN injects i ON i.inject_id = it.inject_id AND i.tenant_id = mt.tenant_id"
              + " UNION"
              + " SELECT mt.organization_id, i.inject_id"
              + " FROM member_teams mt"
              + " JOIN exercises_teams et ON et.team_id = mt.team_id"
              + " JOIN injects i ON i.inject_exercise = et.exercise_id AND i.inject_all_teams"
              + " AND i.tenant_id = mt.tenant_id"
              + " UNION"
              + " SELECT mt.organization_id, i.inject_id"
              + " FROM member_teams mt"
              + " JOIN scenarios_teams st ON st.team_id = mt.team_id"
              + " JOIN injects i ON i.inject_scenario = st.scenario_id AND i.inject_all_teams"
              + " AND i.tenant_id = mt.tenant_id"
              + "), injects_by_organization AS ("
              + " SELECT organization_id, array_agg(inject_id) AS inject_ids"
              + " FROM organization_injects GROUP BY organization_id"
              + "), tags_by_organization AS ("
              + " SELECT ot.organization_id, array_agg(tg.tag_id) AS tag_ids"
              + " FROM organizations o"
              + " JOIN organizations_tags ot ON ot.organization_id = o.organization_id"
              + " JOIN tags tg ON tg.tag_id = ot.tag_id AND tg.tenant_id = o.tenant_id"
              + " WHERE o.tenant_id IN (:tenantIds)"
              + " GROUP BY ot.organization_id"
              + ") "
              + "SELECT org.*, tbo.tag_ids AS organization_tags,"
              + " ibo.inject_ids AS organization_injects,"
              + " coalesce(cardinality(ibo.inject_ids), 0) AS organization_injects_number "
              + "FROM organizations org "
              + "LEFT JOIN tags_by_organization tbo ON tbo.organization_id = org.organization_id "
              + "LEFT JOIN injects_by_organization ibo ON ibo.organization_id = org.organization_id "
              + "WHERE org.tenant_id IN (:tenantIds)",
      nativeQuery = true)
  List<RawOrganization> rawAll(@Param("tenantIds") Set<String> tenantIds);
}
