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

  // Native: array containment ("= ANY(array)") is not expressible in JPQL. This is a read-only
  // lookup by attachment key, so no session side effect is lost; the injects join is a v2-active
  // table, so the inspector scopes the result to the caller's transaction scope like any other
  // join to it, and the row comes back only when the caller is authorized for that tenant.
  @Query(
      value =
          "SELECT i.tenant_id FROM communications c"
              + " JOIN injects i ON i.inject_id = c.communication_inject"
              + " WHERE :attachment = ANY(c.communication_attachments)",
      nativeQuery = true)
  Optional<String> findTenantIdByAttachment(@Param("attachment") String attachment);
}
