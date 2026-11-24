package io.openaev.database.repository;

import io.openaev.database.model.PayloadDraft;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PayloadDraftRepository extends CrudRepository<PayloadDraft, String> {

  /** The drafts of a payload, last changed first. */
  List<PayloadDraft> findByIdentityIdOrderByUpdatedAtDesc(String identityId);

  /** The drafts of new actions (no version yet), last changed first. */
  List<PayloadDraft> findByIdentityIsNullOrderByUpdatedAtDesc();

  /** The drafts a user started or saved last, last changed first. */
  @Query(
      "SELECT d FROM PayloadDraft d WHERE d.createdBy.id = :userId OR d.updatedBy.id = :userId"
          + " ORDER BY d.updatedAt DESC")
  List<PayloadDraft> findByUser(@Param("userId") String userId);

  /** Whether a rejected version is already being reworked in a draft. */
  boolean existsByReworkId(String payloadId);

  /** How many drafts each payload has, for the cards. */
  @Query(
      "SELECT d.identity.id AS identityId, COUNT(d) AS count FROM PayloadDraft d"
          + " WHERE d.identity.id IN :identityIds GROUP BY d.identity.id")
  List<IdentityDraftCount> countByIdentityIds(@Param("identityIds") Collection<String> identityIds);

  interface IdentityDraftCount {
    String getIdentityId();

    long getCount();
  }
}
