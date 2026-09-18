package io.openaev.database.repository;

import io.openaev.database.model.Organization;
import io.openaev.database.raw.RawOrganization;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
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
  Optional<Organization> findById(@NotNull String id);

  @NotNull
  List<Organization> findByNameIgnoreCase(@NotNull final String name);

  // Native array_agg builds the read-only projection; no ORM write/listener side effects are lost.
  @Query(
      value =
          "SELECT org.*, "
              + "array_agg(DISTINCT tags.tag_id) FILTER (WHERE tags.tag_id IS NOT NULL) AS organization_tags, "
              + "array_agg(DISTINCT injects.inject_id) FILTER (WHERE injects.inject_id IS NOT NULL) AS organization_injects, "
              + "coalesce(array_length(array_agg(DISTINCT injects.inject_id) FILTER (WHERE injects.inject_id IS NOT NULL), 1), 0) AS organization_injects_number "
              + "FROM organizations org "
              + "LEFT JOIN organizations_tags org_tags ON org.organization_id = org_tags.organization_id "
              + "LEFT JOIN tags ON tags.tag_id = org_tags.tag_id AND tags.tenant_id = org.tenant_id "
              + "LEFT JOIN users ON users.user_organization = org.organization_id "
              + "LEFT JOIN users_teams ON users.user_id = users_teams.user_id "
              + "LEFT JOIN teams ON teams.team_id = users_teams.team_id AND teams.tenant_id = org.tenant_id "
              + "LEFT JOIN injects_teams ON injects_teams.team_id = teams.team_id "
              + "LEFT JOIN injects ON (injects.inject_id = injects_teams.inject_id OR injects.inject_all_teams) "
              + "AND injects.tenant_id = org.tenant_id "
              + "WHERE org.tenant_id IN (:tenantIds) "
              + "GROUP BY org.organization_id",
      nativeQuery = true)
  List<RawOrganization> rawAll(@Param("tenantIds") Set<String> tenantIds);
}
