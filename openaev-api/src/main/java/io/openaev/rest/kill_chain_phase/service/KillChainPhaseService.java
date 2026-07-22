package io.openaev.rest.kill_chain_phase.service;

import io.openaev.database.model.KillChainPhase;
import io.openaev.database.repository.KillChainPhaseRepository;
import io.openaev.helper.StreamHelper;
import io.openaev.rest.kill_chain_phase.KillChainPhaseUtils;
import io.openaev.rest.kill_chain_phase.form.KillChainPhaseCreateInput;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class KillChainPhaseService {

  private final KillChainPhaseRepository killChainPhaseRepository;

  /**
   * Upserts a batch of kill chain phases.
   *
   * <p>The database unique key is {@code (phase_stix_id, tenant_id)} while collectors identify
   * phases by {@code (kill chain name, short name)}. To stay consistent with both, an existing
   * phase is resolved by STIX id first and by natural key as a fallback. The input batch is also
   * de-duplicated: MITRE bundles can reference the same tactic several times (one per matrix), and
   * persisting two new entities with the same STIX id in one flush violates the constraint.
   *
   * <p>This method is intentionally NOT retried internally (self-invocation would bypass the
   * transactional proxy): on a concurrent-insert constraint violation the whole transaction is
   * rolled back and the endpoint retries once in a fresh transaction.
   */
  @Transactional(rollbackFor = Exception.class)
  public List<KillChainPhase> upsertKillChainPhases(List<KillChainPhaseCreateInput> inputs) {
    // LinkedHashMap keeps collector ordering; the key collapses duplicates within the batch.
    Map<String, KillChainPhase> phasesByKey = new LinkedHashMap<>();
    for (KillChainPhaseCreateInput input : inputs) {
      String key = dedupeKey(input);
      KillChainPhase phase = phasesByKey.get(key);
      if (phase == null) {
        phase = resolveExisting(input).orElseGet(KillChainPhase::new);
        phasesByKey.put(key, phase);
      }
      apply(phase, input);
    }
    return StreamHelper.fromIterable(killChainPhaseRepository.saveAll(phasesByKey.values()));
  }

  private String dedupeKey(KillChainPhaseCreateInput input) {
    if (input.getStixId() != null && !input.getStixId().isBlank()) {
      return "stix:" + input.getStixId();
    }
    return "name:" + input.getKillChainName() + "|" + input.getShortName();
  }

  private Optional<KillChainPhase> resolveExisting(KillChainPhaseCreateInput input) {
    if (input.getStixId() != null && !input.getStixId().isBlank()) {
      Optional<KillChainPhase> byStixId = killChainPhaseRepository.findByStixId(input.getStixId());
      if (byStixId.isPresent()) {
        return byStixId;
      }
    }
    return killChainPhaseRepository.findByKillChainNameAndShortName(
        input.getKillChainName(), input.getShortName());
  }

  private void apply(KillChainPhase phase, KillChainPhaseCreateInput input) {
    boolean isNew = phase.getId() == null;
    phase.setKillChainName(input.getKillChainName());
    phase.setStixId(input.getStixId());
    phase.setExternalId(input.getExternalId());
    phase.setShortName(input.getShortName());
    phase.setName(input.getName());
    phase.setDescription(input.getDescription());
    if (isNew) {
      // Honor an explicit, non-zero order from the input (used by importers that know their own
      // matrix ordering, e.g. MITRE ATLAS); otherwise resolve the canonical order from the
      // kill chain name + short name (mitre-attack or mitre-atlas).
      Long inputOrder = input.getOrder();
      phase.setOrder(
          inputOrder != null && inputOrder != 0L
              ? inputOrder
              : KillChainPhaseUtils.orderFor(input.getKillChainName(), input.getShortName()));
    } else {
      phase.setUpdatedAt(Instant.now());
    }
  }
}
