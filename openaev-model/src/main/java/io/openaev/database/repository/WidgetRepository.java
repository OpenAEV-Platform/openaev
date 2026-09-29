package io.openaev.database.repository;

import io.openaev.database.model.Widget;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WidgetRepository
    extends CrudRepository<Widget, String>, JpaSpecificationExecutor<Widget> {

  List<Widget> findAllByCustomDashboardId(@NotBlank final String id);

  Optional<Widget> findByCustomDashboardIdAndId(
      @NotBlank final String customDashboardId, @NotBlank final String id);
}
