package io.openaev.service.threat_arsenal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.api.threat_arsenal.dto.PayloadDraftOutput;
import io.openaev.api.threat_arsenal.dto.ThreatArsenalAction;
import io.openaev.api.threat_arsenal.dto.ThreatArsenalActionUpdateInput;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Payload;
import io.openaev.database.model.PayloadDraft;
import io.openaev.database.model.User;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.injector_contract.InjectorContractService;
import io.openaev.service.UserService;
import io.openaev.service.content.ContentHistoryService;
import io.openaev.service.payload.PayloadDraftService;
import io.openaev.service.payload.PayloadVersionService;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;

/**
 * Payload drafts of a Threat Arsenal action: versions not published yet, never usable. A draft
 * starts from any version of the action; saving it changes no version number. Publishing it saves a
 * new version from the version it started from, numbered as any update and approved or pending as
 * any update. A draft of a rejected version fixes that version: publishing it sends the same
 * version, same number, for approval again.
 */
@Service
@RequiredArgsConstructor
public class ThreatArsenalDraftService {

  private final InjectorContractService injectorContractService;
  private final PayloadDraftService payloadDraftService;
  private final ThreatArsenalService threatArsenalService;
  private final ContentHistoryService contentHistoryService;
  private final UserService userService;
  private final ObjectMapper mapper;

  /** The drafts of the action (of all its versions), last changed first. */
  public List<PayloadDraftOutput> drafts(String actionId) {
    Payload version = editableVersion(actionId);
    return payloadDraftService.findDrafts(version.getIdentity()).stream()
        .map(this::output)
        .toList();
  }

  /**
   * Starts a draft from the action's version, with the editor's content. A rejected version is
   * fixed in place: at most one draft fixes it.
   */
  public PayloadDraftOutput create(String actionId, JsonNode content) {
    Payload version = editableVersion(actionId);
    boolean rework = version.getApprovalStatus() == Payload.PAYLOAD_APPROVAL_STATUS.REJECTED;
    if (rework && payloadDraftService.isReworked(version)) {
      throw new BadRequestException(
          "Version " + version.getVersion() + " is already being fixed in a draft.");
    }
    PayloadDraft draft = new PayloadDraft();
    draft.setTenant(version.getIdentity().getTenant());
    draft.setIdentity(version.getIdentity());
    draft.setSource(version);
    draft.setSourceVersion(version.getVersion());
    draft.setRework(rework ? version : null);
    User user = userService.currentUserOrNull();
    draft.setCreatedBy(user);
    write(draft, content, user);
    PayloadDraft saved = payloadDraftService.save(draft);
    contentHistoryService.payloadDraftCreated(version, rework);
    return output(saved);
  }

  /** Saves the editor's content in the draft: no version number changes. */
  public PayloadDraftOutput save(String actionId, String draftId, JsonNode content) {
    PayloadDraft draft = draftOf(actionId, draftId);
    write(draft, content, userService.currentUserOrNull());
    return output(payloadDraftService.save(draft));
  }

  /** Drops the draft. No version changes; a rejected version it fixed stays rejected. */
  public void discard(String actionId, String draftId) {
    PayloadDraft draft = draftOf(actionId, draftId);
    Payload from = draft.getRework() != null ? draft.getRework() : draft.getSource();
    if (from != null) {
      contentHistoryService.payloadDraftDiscarded((Payload) Hibernate.unproxy(from));
    }
    payloadDraftService.delete(draft);
  }

  /**
   * Publishes the draft with the editor's content: a new version from the version it started from,
   * or, for a draft fixing a rejected version, that version sent again. The draft is gone after.
   */
  public ThreatArsenalAction publish(
      String actionId, String draftId, ThreatArsenalActionUpdateInput input) {
    PayloadDraft draft = draftOf(actionId, draftId);
    ThreatArsenalAction result;
    if (draft.getRework() != null) {
      result = threatArsenalService.rework(actionOf(draft.getRework()), input);
    } else if (draft.getSource() != null) {
      result = threatArsenalService.update(actionOf(draft.getSource()), input, true);
    } else {
      throw new BadRequestException(
          "The version this draft started from was deleted: start a draft from another version.");
    }
    payloadDraftService.delete(draft);
    return result;
  }

  // The action's payload version, if it can have drafts: a local payload, not a catalogue version
  // (read-only) nor a customized one (changed through its overrides).
  private Payload editableVersion(String actionId) {
    InjectorContract contract = injectorContractService.injectorContract(actionId);
    if (contract.getPayload() == null) {
      throw new BadRequestException("Action " + actionId + " has no payload: it has no versions.");
    }
    Payload version = (Payload) Hibernate.unproxy(contract.getPayload());
    if (PayloadVersionService.isManaged(version) || version.getIdentity().isVariant()) {
      throw new BadRequestException(
          "Action " + actionId + " is read-only here: duplicate or customize it to change it.");
    }
    return version;
  }

  private PayloadDraft draftOf(String actionId, String draftId) {
    return payloadDraftService.findDraft(editableVersion(actionId).getIdentity(), draftId);
  }

  private String actionOf(Payload version) {
    return injectorContractService.findByPayload((Payload) Hibernate.unproxy(version)).getId();
  }

  private void write(PayloadDraft draft, JsonNode content, User user) {
    try {
      draft.setContent(mapper.writeValueAsString(content));
    } catch (JsonProcessingException e) {
      throw new BadRequestException("The draft content is not valid JSON.");
    }
    draft.setUpdatedBy(user);
    draft.setUpdatedByName(user != null ? user.getNameOrEmail() : null);
    draft.setUpdatedAt(Instant.now());
  }

  private PayloadDraftOutput output(PayloadDraft draft) {
    JsonNode content;
    try {
      content = mapper.readTree(draft.getContent());
    } catch (JsonProcessingException e) {
      content = mapper.createObjectNode();
    }
    String sourceActionId =
        draft.getSource() == null
            ? null
            : injectorContractService
                .findOptionalByPayload((Payload) Hibernate.unproxy(draft.getSource()))
                .map(InjectorContract::getId)
                .orElse(null);
    return new PayloadDraftOutput(
        draft.getId(),
        sourceActionId,
        draft.getSourceVersion(),
        draft.getRework() != null,
        content,
        draft.getUpdatedByName(),
        draft.getCreatedAt(),
        draft.getUpdatedAt());
  }
}
