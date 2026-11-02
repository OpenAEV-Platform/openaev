package io.openaev.database.repository;

import io.openaev.database.model.PayloadVersion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PayloadVersionRepository extends JpaRepository<PayloadVersion, String> {

  /** The versions of a payload, newest first. */
  List<PayloadVersion> findByPayloadIdOrderByNumberDesc(String payloadId);

  /** The version of a payload in the given status (PENDING: at most one). */
  Optional<PayloadVersion> findFirstByPayloadIdAndStatus(
      String payloadId, PayloadVersion.STATUS status);

  /** The highest version number of a payload, 1 when it has no stored version yet. */
  @Query("SELECT COALESCE(MAX(v.number), 1) FROM PayloadVersion v WHERE v.payload.id = :payloadId")
  int maxNumber(@Param("payloadId") String payloadId);

  /** The number of the latest approved version of a payload, 1 when none is stored. */
  @Query(
      "SELECT COALESCE(MAX(v.number), 1) FROM PayloadVersion v WHERE v.payload.id = :payloadId"
          + " AND v.status = io.openaev.database.model.PayloadVersion.STATUS.APPROVED")
  int activeNumber(@Param("payloadId") String payloadId);
}
