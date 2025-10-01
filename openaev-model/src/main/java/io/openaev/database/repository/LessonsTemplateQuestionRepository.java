package io.openaev.database.repository;

import io.openaev.database.model.LessonsTemplateQuestion;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LessonsTemplateQuestionRepository
    extends CrudRepository<LessonsTemplateQuestion, String>,
        JpaSpecificationExecutor<LessonsTemplateQuestion> {

  @NotNull
  Optional<LessonsTemplateQuestion> findById(@NotNull String id);
}
