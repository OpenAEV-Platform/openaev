package io.openaev.database.repository;

import io.openaev.database.model.Pause;
import io.openaev.database.raw.RawPause;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PauseRepository
    extends CrudRepository<Pause, String>, JpaSpecificationExecutor<Pause> {

  @NotNull
  Optional<Pause> findById(@NotNull String id);

  @Query(value = "select p from Pause p where p.exercise.id = :exerciseId")
  List<Pause> findAllForExercise(@Param("exerciseId") String exerciseId);

  @Query(
      value =
          "SELECT p.pause_id, p.pause_date, p.pause_duration, p.pause_exercise "
              + "FROM pauses p "
              + "WHERE p.pause_exercise = :exerciseId",
      nativeQuery = true)
  List<RawPause> rawAllForExercise(@Param("exerciseId") String exerciseId);
}
