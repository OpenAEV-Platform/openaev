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
}
