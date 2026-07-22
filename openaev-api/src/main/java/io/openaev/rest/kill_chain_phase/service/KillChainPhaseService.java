package io.openaev.rest.kill_chain_phase.service;

import io.openaev.database.model.KillChainPhase;
import io.openaev.database.repository.KillChainPhaseRepository;
import io.openaev.helper.StreamHelper;
import io.openaev.rest.kill_chain_phase.KillChainPhaseUtils;
import io.openaev.rest.kill_chain_phase.form.KillChainPhaseCreateInput;
import java.time.Instant;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    // In-batch de-duplication tracks pending entities under BOTH unique keys the database
    // enforces (STIX id and natural key). Keying on a single one is not enough: the same phase
    // can appear once with a STIX id and once without, or two entries can share a STIX id under
    // different natural keys — either way a single-key map would create two entities and
    // deterministically violate a constraint at flush, which no retry can fix.
    Map<String, KillChainPhase> byNaturalKey = new LinkedHashMap<>();
    Map<String, KillChainPhase> byStixId = new LinkedHashMap<>();
    for (KillChainPhaseCreateInput input : inputs) {
      String naturalKey = naturalKey(input);
      String stixId = normalizedStixId(input);
      KillChainPhase phase = byNaturalKey.get(naturalKey);
      if (phase == null && stixId != null) {
        phase = byStixId.get(stixId);
      }
      if (phase == null) {
        phase = resolveExisting(input).orElseGet(KillChainPhase::new);
      }
      apply(phase, input);
      byNaturalKey.put(naturalKey, phase);
      if (stixId != null) {
        byStixId.put(stixId, phase);
      }
    }
    // The same entity instance can be registered under several natural keys when entries share
    // a STIX id, but it must only be persisted once. Identity-based de-duplication on purpose:
    // KillChainPhase#equals dereferences the id, which is still null for new entities.
    Set<KillChainPhase> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    List<KillChainPhase> phases = byNaturalKey.values().stream().filter(seen::add).toList();
    return StreamHelper.fromIterable(killChainPhaseRepository.saveAll(phases));
  }

  private String naturalKey(KillChainPhaseCreateInput input) {
    return input.getKillChainName() + "|" + input.getShortName();
  }

  private String normalizedStixId(KillChainPhaseCreateInput input) {
    return input.getStixId() != null && !input.getStixId().isBlank() ? input.getStixId() : null;
  }

  private Optional<KillChainPhase> resolveExisting(KillChainPhaseCreateInput input) {
    String stixId = normalizedStixId(input);
    if (stixId != null) {
      Optional<KillChainPhase> byStixId = killChainPhaseRepository.findByStixId(stixId);
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
    // Never clobber a known STIX id with null: entries without a STIX id can target the same
    // phase as entries with one, and the STIX id is part of the database unique key.
    String stixId = normalizedStixId(input);
    if (stixId != null) {
      phase.setStixId(stixId);
    }
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
