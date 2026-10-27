package io.openaev.service.payload;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Payload;
import io.openaev.database.model.PayloadArgument;
import io.openaev.database.model.StatusPayload;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;

/**
 * What an execution records about its payload (ADR-011): the version that ran, the hash of the
 * content computed when it ran, and the argument values the inject gave it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayloadExecutionSnapshotService {

  private final PayloadContentHashService payloadContentHashService;

  public StatusPayload stamp(StatusPayload snapshot, Inject inject) {
    if (snapshot == null || snapshot.getPayloadId() != null) {
      return snapshot;
    }
    InjectorContract contract = inject.getInjectorContract().orElse(null);
    if (contract == null || contract.getPayload() == null) {
      return snapshot;
    }
    Payload payload = (Payload) Hibernate.unproxy(contract.getPayload());
    snapshot.setPayloadId(payload.getId());
    snapshot.setVersion(payload.getVersion());
    snapshot.setContentHash(hashWhenRun(payload, contract));
    snapshot.setArgumentValues(argumentValues(payload, inject));
    return snapshot;
  }

  // Never blocks the execution: a missing hash or a mismatch is only traced.
  private String hashWhenRun(Payload payload, InjectorContract contract) {
    String hash;
    try {
      hash = payloadContentHashService.hash(payload, contract.getAttackPatterns());
    } catch (UncheckedIOException e) {
      log.error("Payload {} could not be hashed when it ran", payload.getId(), e);
      return null;
    }
    if (hash == null) {
      log.error("Payload {} ran with a file that cannot be read: no content hash", payload.getId());
      return null;
    }
    if (payload.getContentHash() != null && !payload.getContentHash().equalsIgnoreCase(hash)) {
      log.error(
          "Integrity: payload {} v{} ran with content hash {} instead of {}",
          payload.getId(),
          payload.getVersion(),
          hash,
          payload.getContentHash());
    }
    return hash;
  }

  // The command blocks keep their #{variables}: the values they ran with are recorded next to them.
  private static Map<String, String> argumentValues(Payload payload, Inject inject) {
    Map<String, String> values = new LinkedHashMap<>();
    for (PayloadArgument argument :
        Optional.ofNullable(payload.getArguments()).orElseGet(java.util.List::of)) {
      JsonNode value =
          inject.getContent() == null ? null : inject.getContent().get(argument.getKey());
      values.put(
          argument.getKey(),
          value != null && !value.isNull() ? value.asText() : argument.getDefaultValue());
    }
    return values;
  }
}
