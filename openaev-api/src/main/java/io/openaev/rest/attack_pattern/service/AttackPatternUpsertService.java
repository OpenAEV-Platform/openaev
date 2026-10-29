package io.openaev.rest.attack_pattern.service;

import static io.openaev.helper.StreamHelper.fromIterable;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import io.openaev.config.TenantWriteScopeResolver;
import io.openaev.context.TxCtx;
import io.openaev.database.model.AttackPattern;
import io.openaev.database.model.KillChainPhase;
import io.openaev.database.repository.AttackPatternRepository;
import io.openaev.database.repository.KillChainPhaseRepository;
import io.openaev.rest.attack_pattern.form.AttackPatternCreateInput;
import io.openaev.rest.exception.ElementNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class AttackPatternUpsertService {

  private final AttackPatternRepository attackPatternRepository;
  private final KillChainPhaseRepository killChainPhaseRepository;
  private final TenantWriteScopeResolver writeScopeResolver;

  /** Creates or updates attack patterns by external id, one upsert at a time per tenant. */
  public List<AttackPattern> upsert(
      TxCtx ctx, List<AttackPatternCreateInput> inputs, Boolean ignoreDependencies) {
    String tenantId = writeScopeResolver.tenantForWrite(ctx, null);
    // Concurrent imports lock the same rows and would queue up until the pool is exhausted.
    if (!attackPatternRepository.tryLockUpsert(tenantId)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "An attack pattern upsert is already running");
    }
    // Parents first, so children can resolve them.
    List<AttackPattern> upserted =
        new ArrayList<>(
            upsertBatch(
                tenantId,
                inputs.stream().filter(i -> i.getParentId() == null).toList(),
                ignoreDependencies));
    upserted.addAll(
        upsertBatch(
            tenantId,
            inputs.stream().filter(i -> i.getParentId() != null).toList(),
            ignoreDependencies));
    return upserted;
  }

  private List<AttackPattern> upsertBatch(
      String tenantId, List<AttackPatternCreateInput> inputs, Boolean ignoreDependencies) {
    if (inputs.isEmpty()) {
      return List.of();
    }
    Map<String, AttackPattern> existingByExternalId = loadExisting(inputs, tenantId);
    Map<String, KillChainPhase> phasesById = loadPhases(inputs);
    Map<String, AttackPattern> parentsByStixId = loadParents(inputs, tenantId);
    List<AttackPattern> upserted = new ArrayList<>();
    for (AttackPatternCreateInput input : inputs) {
      List<KillChainPhase> phases = resolvePhases(input, phasesById);
      AttackPattern parent = resolveParent(input, parentsByStixId);
      AttackPattern existing = existingByExternalId.get(input.getExternalId());
      if (existing == null) {
        AttackPattern created = input.toAttackPattern(tenantId);
        created.setKillChainPhases(phases);
        created.setParent(parent);
        upserted.add(created);
      } else {
        update(existing, input, phases, parent, ignoreDependencies);
        upserted.add(existing);
      }
    }
    return fromIterable(attackPatternRepository.saveAll(upserted));
  }

  private Map<String, AttackPattern> loadExisting(
      List<AttackPatternCreateInput> inputs, String tenantId) {
    return attackPatternRepository
        .findAllByExternalIdInAndTenantIdWithKillChainPhases(
            inputs.stream().map(AttackPatternCreateInput::getExternalId).toList(), tenantId)
        .stream()
        .collect(toMap(AttackPattern::getExternalId, identity(), (a, b) -> a));
  }

  private Map<String, KillChainPhase> loadPhases(List<AttackPatternCreateInput> inputs) {
    Set<String> ids =
        inputs.stream()
            .map(AttackPatternCreateInput::getKillChainPhasesIds)
            .filter(Objects::nonNull)
            .flatMap(List::stream)
            .collect(toSet());
    if (ids.isEmpty()) {
      return Map.of();
    }
    return fromIterable(killChainPhaseRepository.findAllById(ids)).stream()
        .collect(toMap(KillChainPhase::getId, identity()));
  }

  private Map<String, AttackPattern> loadParents(
      List<AttackPatternCreateInput> inputs, String tenantId) {
    Set<String> stixIds =
        inputs.stream()
            .map(AttackPatternCreateInput::getParentId)
            .filter(Objects::nonNull)
            .collect(toSet());
    if (stixIds.isEmpty()) {
      return Map.of();
    }
    return attackPatternRepository.findAllByStixIdInAndTenantId(stixIds, tenantId).stream()
        .collect(toMap(AttackPattern::getStixId, identity(), (a, b) -> a));
  }

  private static List<KillChainPhase> resolvePhases(
      AttackPatternCreateInput input, Map<String, KillChainPhase> phasesById) {
    if (input.getKillChainPhasesIds() == null) {
      return new ArrayList<>();
    }
    return input.getKillChainPhasesIds().stream()
        .distinct()
        .map(phasesById::get)
        .filter(Objects::nonNull)
        .collect(toCollection(ArrayList::new));
  }

  private static AttackPattern resolveParent(
      AttackPatternCreateInput input, Map<String, AttackPattern> parentsByStixId) {
    if (input.getParentId() == null) {
      return null;
    }
    AttackPattern parent = parentsByStixId.get(input.getParentId());
    if (parent == null) {
      throw new ElementNotFoundException();
    }
    return parent;
  }

  private static void update(
      AttackPattern attackPattern,
      AttackPatternCreateInput input,
      List<KillChainPhase> phases,
      AttackPattern parent,
      Boolean ignoreDependencies) {
    // The input may omit kill chain phases or parent: keep the stored ones.
    if (ignoreDependencies) {
      if (phases.isEmpty() && !attackPattern.getKillChainPhases().isEmpty()) {
        phases = attackPattern.getKillChainPhases();
      }
      if (parent == null && attackPattern.getParent() != null) {
        parent = attackPattern.getParent();
      }
    }
    attackPattern.setStixId(input.getStixId());
    attackPattern.setKillChainPhases(phases);
    attackPattern.setName(input.getName());
    attackPattern.setDescription(input.getDescription());
    attackPattern.setPlatforms(input.getPlatforms());
    attackPattern.setPermissionsRequired(input.getPermissionsRequired());
    attackPattern.setParent(parent);
  }
}
