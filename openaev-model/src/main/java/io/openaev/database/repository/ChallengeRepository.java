package io.openaev.database.repository;

import io.openaev.database.model.Challenge;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ChallengeRepository
    extends CrudRepository<Challenge, String>, JpaSpecificationExecutor<Challenge> {

  /**
   * Looks up a challenge by its ID using a JPQL query so that the active Hibernate tenant filter
   * ({@code tenantFilter}) is applied. {@code EntityManager.find()} bypasses Hibernate filters and
   * must not be used directly for tenant-scoped entities.
   */
  @NotNull
  @Query("SELECT c FROM Challenge c WHERE c.id = :id")
  Optional<Challenge> findById(@NotNull @Param("id") final String id);

  @NotNull
  List<Challenge> findByNameIgnoreCase(@NotNull final String name);
}
