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

  // Native array_agg builds the read-only projection; no ORM write/listener side effects are lost.
  @Query(
      value =
          "SELECT org.*, "
              + "(SELECT array_agg(ot.tag_id) FROM organizations_tags ot "
              + " JOIN tags t ON t.tag_id = ot.tag_id AND t.tenant_id = org.tenant_id "
              + " WHERE ot.organization_id = org.organization_id) AS organization_tags, "
              + "oi.ids AS organization_injects, "
              + "coalesce(array_length(oi.ids, 1), 0) AS organization_injects_number "
              + "FROM organizations org "
              // Injects are aggregated in a lateral subquery: joining them with
              // `OR inject_all_teams` multiplied every organization row by all the
              // "all teams" injects of the tenant (#8926).
              + "LEFT JOIN LATERAL ("
              + " SELECT array_agg(DISTINCT x.inject_id) AS ids FROM ("
              + "  SELECT it.inject_id FROM users u "
              + "  JOIN users_teams ut ON ut.user_id = u.user_id "
              + "  JOIN teams tm ON tm.team_id = ut.team_id AND tm.tenant_id = org.tenant_id "
              + "  JOIN injects_teams it ON it.team_id = tm.team_id "
              + "  JOIN injects i ON i.inject_id = it.inject_id AND i.tenant_id = org.tenant_id "
              + "  WHERE u.user_organization = org.organization_id "
              + "  UNION "
              + "  SELECT i.inject_id FROM injects i "
              + "  WHERE i.inject_all_teams AND i.tenant_id = org.tenant_id "
              + "  AND EXISTS (SELECT 1 FROM users u "
              + "   JOIN users_teams ut ON ut.user_id = u.user_id "
              + "   JOIN teams tm ON tm.team_id = ut.team_id AND tm.tenant_id = org.tenant_id "
              + "   WHERE u.user_organization = org.organization_id)"
              + " ) x"
              + ") oi ON true "
              + "WHERE org.tenant_id IN (:tenantIds)",
      nativeQuery = true)
  List<RawOrganization> rawAll(@Param("tenantIds") Set<String> tenantIds);
}
