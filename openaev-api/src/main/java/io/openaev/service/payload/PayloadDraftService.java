package io.openaev.service.payload;

import io.openaev.database.model.Payload;
import io.openaev.database.model.PayloadDraft;
import io.openaev.database.model.PayloadIdentity;
import io.openaev.database.repository.PayloadDraftRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import jakarta.validation.constraints.NotNull;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The drafts of payload versions (payload_drafts, tenant-v2 active): the only accessor of the
 * table. Its callers run inside the TxCtx-carrying Threat Arsenal API transactions.
 */
@Service
@RequiredArgsConstructor
public class PayloadDraftService {

  private final PayloadDraftRepository payloadDraftRepository;

  /** The drafts of a payload, last changed first. */
  public List<PayloadDraft> findDrafts(@NotNull final PayloadIdentity identity) {
    return payloadDraftRepository.findByIdentityIdOrderByUpdatedAtDesc(identity.getId());
  }

  /** A draft of this payload; not found when it belongs to another one. */
  public PayloadDraft findDraft(@NotNull final PayloadIdentity identity, final String draftId) {
    return payloadDraftRepository
        .findById(draftId)
        .filter(
            draft ->
                draft.getIdentity() != null && draft.getIdentity().getId().equals(identity.getId()))
        .orElseThrow(() -> new ElementNotFoundException("Draft not found: " + draftId));
  }

  /** The drafts of new actions (no version yet), last changed first. */
  public List<PayloadDraft> findNewActionDrafts() {
    return payloadDraftRepository.findByIdentityIsNullOrderByUpdatedAtDesc();
  }

  /** A draft of a new action; not found when it belongs to an existing payload. */
  public PayloadDraft findNewActionDraft(final String draftId) {
    return payloadDraftRepository
        .findById(draftId)
        .filter(draft -> draft.getIdentity() == null)
        .orElseThrow(() -> new ElementNotFoundException("Draft not found: " + draftId));
  }

  /** Whether a rejected version is already being fixed in a draft. */
  public boolean isReworked(@NotNull final Payload version) {
    return payloadDraftRepository.existsByReworkId(version.getId());
  }

  public PayloadDraft save(@NotNull final PayloadDraft draft) {
    return payloadDraftRepository.save(draft);
  }

  public void delete(@NotNull final PayloadDraft draft) {
    payloadDraftRepository.delete(draft);
  }

  /** How many drafts each payload has, by identity id (absent: none). */
  public Map<String, Long> countByIdentityIds(@NotNull final Collection<String> identityIds) {
    if (identityIds.isEmpty()) {
      return Map.of();
    }
    return payloadDraftRepository.countByIdentityIds(identityIds).stream()
        .collect(
            Collectors.toMap(
                PayloadDraftRepository.IdentityDraftCount::getIdentityId,
                PayloadDraftRepository.IdentityDraftCount::getCount));
  }
}
