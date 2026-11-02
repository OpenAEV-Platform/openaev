package io.openaev.database.repository;

import io.openaev.database.model.PayloadApproval;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PayloadApprovalRepository extends CrudRepository<PayloadApproval, String> {

  /** The approval history of a payload, newest first. */
  List<PayloadApproval> findByPayloadIdOrderByCreatedAtDesc(String payloadId);

  /** The latest entry of a payload's approval history. */
  Optional<PayloadApproval> findFirstByPayloadIdOrderByCreatedAtDesc(String payloadId);

  /**
   * How many different executable contents of a payload were approved (entries without a
   * fingerprint, from the initial migration, are not counted).
   */
  @Query(
      "SELECT COUNT(DISTINCT a.fingerprint) FROM PayloadApproval a WHERE a.payload.id = :payloadId"
          + " AND a.status = io.openaev.database.model.Payload.PAYLOAD_APPROVAL_STATUS.APPROVED")
  long countApprovedContents(@Param("payloadId") String payloadId);

  /** The latest entry of a payload with the given status (e.g. who sent it to pending). */
  Optional<PayloadApproval> findFirstByPayloadIdAndStatusOrderByCreatedAtDesc(
      String payloadId, io.openaev.database.model.Payload.PAYLOAD_APPROVAL_STATUS status);
}
