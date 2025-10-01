package io.openaev.database.repository;

import io.openaev.database.model.Challenge;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChallengeRepository
    extends CrudRepository<Challenge, String>, JpaSpecificationExecutor<Challenge> {

  @NotNull
  Optional<Challenge> findById(@NotNull final String id);

  @NotNull
  List<Challenge> findByNameIgnoreCase(@NotNull final String name);
}
