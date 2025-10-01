package io.openaev.database.repository;

import io.openaev.database.model.LessonsTemplate;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LessonsTemplateRepository
    extends CrudRepository<LessonsTemplate, String>, JpaSpecificationExecutor<LessonsTemplate> {

  @NotNull
  Optional<LessonsTemplate> findById(@NotNull String id);
}
