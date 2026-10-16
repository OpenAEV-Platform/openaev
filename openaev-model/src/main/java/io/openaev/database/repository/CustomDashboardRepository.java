package io.openaev.database.repository;

import io.openaev.database.model.CustomDashboard;
import io.openaev.database.raw.RawCustomDashboard;
import jakarta.validation.constraints.NotBlank;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomDashboardRepository
    extends CrudRepository<CustomDashboard, String>, JpaSpecificationExecutor<CustomDashboard> {

  Optional<CustomDashboard> findByName(@NotBlank final String name);

  /**
   * Loads the {@code custom_dashboard_widgets} of the given dashboards inside the scoped
   * transaction, for the search endpoint that returns the raw entity: a lazy {@code widgets}
   * serialized open-in-view after the commit fails closed to an empty array. Run it in the same
   * session as the query that loaded the dashboards: Hibernate fills the widgets of the entities it
   * already manages. The join keeps only widgets of the dashboard's own tenant, so a scope covering
   * several tenants cannot attach another tenant's widget.
   */
  @Query(
      "select distinct cd from CustomDashboard cd left join fetch cd.widgets w"
          + " where cd.id in :ids and (w.id is null or w.tenant = cd.tenant)")
  List<CustomDashboard> findAllByIdInFetchingWidgets(@Param("ids") Collection<String> ids);

  /**
   * Get the raw version of the custom dashboards
   *
   * @return the list of custom dashboards
   */
  @Query(
      value =
          " SELECT cd.custom_dashboard_id, "
              + "cd.custom_dashboard_name "
              + "FROM custom_dashboards cd",
      nativeQuery = true)
  List<RawCustomDashboard> rawAll();

  @Query(
      value =
          """
      select cd.* from custom_dashboards cd
      join scenarios s on s.scenario_custom_dashboard = cd.custom_dashboard_id
      where s.scenario_id = :resourceId
      and s.tenant_id = cd.tenant_id
      union
      select cd.* from custom_dashboards cd
      join exercises e on e.exercise_custom_dashboard = cd.custom_dashboard_id
      where e.exercise_id = :resourceId
      and e.tenant_id = cd.tenant_id
      """,
      nativeQuery = true)
  Optional<CustomDashboard> findByResourceId(String resourceId);

  @Modifying
  @Query(
      value = "DELETE FROM custom_dashboards cd WHERE cd.custom_dashboard_id = :customDashboardId",
      nativeQuery = true)
  int deleteByIdNative(@Param("customDashboardId") String customDashboardId);
}
