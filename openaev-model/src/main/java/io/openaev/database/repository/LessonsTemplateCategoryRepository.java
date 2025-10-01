package io.openaev.database.repository;

import io.openaev.database.model.LessonsTemplateCategory;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LessonsTemplateCategoryRepository
    extends CrudRepository<LessonsTemplateCategory, String>,
        JpaSpecificationExecutor<LessonsTemplateCategory> {

  @NotNull
  Optional<LessonsTemplateCategory> findById(@NotNull String id);
}
