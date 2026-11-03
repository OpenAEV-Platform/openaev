package io.openaev.database.repository;

import io.openaev.database.model.PayloadApproval;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PayloadApprovalRepository extends CrudRepository<PayloadApproval, String> {

  /** The approval history of a payload, newest first. */
  List<PayloadApproval> findByPayloadIdOrderByCreatedAtDesc(String payloadId);

  /** The latest entry of a payload's approval history. */
  Optional<PayloadApproval> findFirstByPayloadIdOrderByCreatedAtDesc(String payloadId);

  /** Whether the status of this version was already decided (a version is decided once). */
  boolean existsByPayloadId(String payloadId);

  /** The latest entry of a payload with the given status (e.g. who sent it to pending). */
  Optional<PayloadApproval> findFirstByPayloadIdAndStatusOrderByCreatedAtDesc(
      String payloadId, io.openaev.database.model.Payload.PAYLOAD_APPROVAL_STATUS status);
}
