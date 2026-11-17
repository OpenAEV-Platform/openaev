package io.openaev.database.repository;

import io.openaev.database.model.Communication;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CommunicationRepository
    extends CrudRepository<Communication, String>, JpaSpecificationExecutor<Communication> {

  @NotNull
  Optional<Communication> findById(@NotNull String id);

  List<Communication> findByInjectId(@NotNull String injectId);

  boolean existsByIdentifierAndInjectTenantId(String identifier, String tenantId);

  @Query("SELECT COUNT(c) FROM Communication c WHERE c.inject.exercise.id = :exerciseId")
  long countByExerciseId(@Param("exerciseId") String exerciseId);

  // Native: a text[] containment check (`= ANY(...)`) has no clean JPQL/Criteria equivalent, and
  // this is a plain read with no listener-dependent side effect to lose. The attachment path
  // embeds the communication's own (globally unique) id, so at most one row ever matches.
  @Query(
      value = "SELECT * FROM communications WHERE :attachment = ANY(communication_attachments)",
      nativeQuery = true)
  Optional<Communication> findByAttachment(@Param("attachment") String attachment);
}
