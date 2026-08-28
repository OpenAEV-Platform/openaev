package io.openaev.database.repository;

import io.openaev.database.model.Channel;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ChannelRepository
    extends CrudRepository<Channel, String>, JpaSpecificationExecutor<Channel> {

  /**
   * Looks up a channel by its ID using a JPQL query so that the active Hibernate tenant filter
   * ({@code tenantFilter}) is applied. {@code EntityManager.find()} (used by the default
   * CrudRepository#findById) bypasses Hibernate filters and must not be used directly for
   * tenant-scoped entities. Same fix as ChallengeRepository (#6027).
   */
  @NotNull
  @Query("SELECT c FROM Channel c WHERE c.id = :id")
  Optional<Channel> findById(@NotNull @Param("id") String id);

  List<Channel> findByNameIgnoreCase(String name);

  List<Channel> findDistinctByArticlesExerciseId(String simulationId);

  List<Channel> findDistinctByArticlesScenarioId(String scenarioId);
}
