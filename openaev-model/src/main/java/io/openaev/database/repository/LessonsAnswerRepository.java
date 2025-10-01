package io.openaev.database.repository;

import io.openaev.database.model.LessonsAnswer;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LessonsAnswerRepository
    extends CrudRepository<LessonsAnswer, String>, JpaSpecificationExecutor<LessonsAnswer> {

  @NotNull
  Optional<LessonsAnswer> findById(@NotNull String id);

  Optional<LessonsAnswer> findByUserIdAndQuestionId(
      @NotNull String userId, @NotNull String questionId);
}
