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
}
