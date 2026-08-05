package io.openaev.database.repository;

import io.openaev.database.model.ConnectorCompositeId;
import io.openaev.database.model.Executor;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ExecutorRepository extends CrudRepository<Executor, ConnectorCompositeId> {

  @Query("SELECT e FROM Executor e WHERE e.id = :id")
  Optional<Executor> findByExecutorId(@Param("id") @NotNull String id);

  Optional<Executor> findByType(@NotNull String type);

  @Modifying
  @Query("DELETE FROM Executor e WHERE e.id = :id")
  void deleteByExecutorId(@Param("id") String id);
}
