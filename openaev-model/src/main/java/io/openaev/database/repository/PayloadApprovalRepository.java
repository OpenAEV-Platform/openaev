package io.openaev.database.repository;

import io.openaev.database.model.PayloadApproval;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PayloadApprovalRepository extends CrudRepository<PayloadApproval, String> {

  /** The approval history of a payload, newest first. */
  List<PayloadApproval> findByPayloadIdOrderByCreatedAtDesc(String payloadId);

  /**
   * The approval histories of several versions at once (one query; the actor is fetched with each
   * entry for its name).
   */
  @EntityGraph(attributePaths = "actor")
  List<PayloadApproval> findByPayloadIdIn(Collection<String> payloadIds);

  /** The latest entry of a payload's approval history. */
  Optional<PayloadApproval> findFirstByPayloadIdOrderByCreatedAtDesc(String payloadId);

  /** Whether the status of this version was already decided (a version is decided once). */
  boolean existsByPayloadId(String payloadId);
}
