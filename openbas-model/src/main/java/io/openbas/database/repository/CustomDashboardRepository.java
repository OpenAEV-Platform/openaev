package io.openbas.database.repository;

import io.openbas.database.model.CustomDashboard;
import io.openbas.database.raw.RawCustomDashboard;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomDashboardRepository
    extends CrudRepository<CustomDashboard, String>, JpaSpecificationExecutor<CustomDashboard> {

  Optional<CustomDashboard> findByName(@NotBlank final String name);

  /**
   * Get the raw version of the custom dashboards
   *
   * @return the list of custom dashboards
   */
  @Query(
      value =
          " SELECT cd.custom_dashboard_id, "
              + "cd.custom_dashboard_name "
              + "FROM custom_dashboards cd;",
      nativeQuery = true)
  List<RawCustomDashboard> rawAll();

  @Query(
      value =
          """
      select cd.* from custom_dashboards cd
      join scenarios s on s.scenario_custom_dashboard = cd.custom_dashboard_id
      where s.scenario_id = :resourceId
      union
      select cd.* from custom_dashboards cd
      join exercises e on e.exercise_custom_dashboard = cd.custom_dashboard_id
      where e.exercise_id = :resourceId
      """,
      nativeQuery = true)
  Optional<CustomDashboard> findByResourceId(String resourceId);

  @Query(
      """
  SELECT d FROM CustomDashboard d
  WHERE d.id = (
    SELECT s.value FROM Setting s
    WHERE s.key = 'platform_home_dashboard'
  )
  """)
  Optional<CustomDashboard> findHomeDashboard();
}
