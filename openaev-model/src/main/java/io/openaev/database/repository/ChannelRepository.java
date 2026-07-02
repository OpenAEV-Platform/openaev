package io.openaev.database.repository;

import io.openaev.database.model.Channel;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChannelRepository
    extends CrudRepository<Channel, String>, JpaSpecificationExecutor<Channel> {

  @NotNull
  Optional<Channel> findById(@NotNull String id);

  List<Channel> findByNameIgnoreCase(String name);

  List<Channel> findDistinctByArticlesExerciseId(String simulationId);

  List<Channel> findDistinctByArticlesScenarioId(String scenarioId);
}
