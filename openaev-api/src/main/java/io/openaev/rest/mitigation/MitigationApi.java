package io.openaev.rest.mitigation;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.openaev.aop.AccessControl;
import io.openaev.config.TenantWriteScopeResolver;
import io.openaev.context.TxCtx;
import io.openaev.database.model.AttackPattern;
import io.openaev.database.model.Mitigation;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.AttackPatternRepository;
import io.openaev.database.repository.MitigationRepository;
import io.openaev.database.specification.AttackPatternSpecification;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.mitigation.form.MitigationCreateInput;
import io.openaev.rest.mitigation.form.MitigationUpdateInput;
import io.openaev.rest.mitigation.form.MitigationUpsertInput;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping({MitigationApi.MITIGATION_URI, MitigationApi.TENANT_MITIGATION_URI})
public class MitigationApi extends RestBehavior {

  public static final String MITIGATION_URI = "/api/mitigations";
  public static final String TENANT_MITIGATION_URI = TENANT_PREFIX + "/mitigations";

  private final MitigationRepository mitigationRepository;
  private final AttackPatternRepository attackPatternRepository;
  private final TenantWriteScopeResolver writeScopeResolver;

  // -- READ --

  @GetMapping
  @Transactional
  @AccessControl(
      skipRBAC =
          true) // TODO: Mitigation API is not called anywhere yet (by us or opencti), so no RBAC
  // yet
  // TxCtx is resolved from the request and applied by the transaction aspect; it scopes this read
  // to the caller's tenants. The handler does not use it directly.
  public Iterable<Mitigation> mitigations(TxCtx ctx) {
    return mitigationRepository.findAll();
  }

  @PostMapping("/search")
  @Transactional
  @AccessControl(
      skipRBAC =
          true) // TODO: Mitigation API is not called anywhere yet (by us or opencti), so no RBAC
  // yet
  // TxCtx scopes the search to the caller's tenants. The handler does not use it directly.
  public Page<Mitigation> mitigations(
      TxCtx ctx, @RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    return buildPaginationJPA(
        (Specification<Mitigation> specification, Pageable pageable) ->
            this.mitigationRepository.findAll(specification, pageable),
        searchPaginationInput,
        Mitigation.class);
  }

  @GetMapping("/{mitigationId}")
  @Transactional
  @AccessControl(
      skipRBAC =
          true) // TODO: Mitigation API is not called anywhere yet (by us or opencti), so no RBAC
  // yet
  // TxCtx scopes this read to the caller's tenants. The handler does not use it directly.
  public Mitigation mitigation(TxCtx ctx, @PathVariable String mitigationId) {
    return mitigationRepository.findById(mitigationId).orElseThrow(ElementNotFoundException::new);
  }

  @GetMapping("/{mitigationId}/attack_patterns")
  @Transactional
  @AccessControl(
      skipRBAC =
          true) // TODO: Mitigation API is not called anywhere yet (by us or opencti), so no RBAC
  // yet
  // TxCtx scopes the parent lookup to the caller's tenants. The handler does not use it directly.
  public Iterable<AttackPattern> injectorContracts(TxCtx ctx, @PathVariable String mitigationId) {
    mitigationRepository.findById(mitigationId).orElseThrow(ElementNotFoundException::new);
    return attackPatternRepository.findAll(
        AttackPatternSpecification.fromAttackPattern(mitigationId));
  }

  // -- CREATE --

  @PostMapping
  @AccessControl(
      skipRBAC =
          true) // TODO: Mitigation API is not called anywhere yet (by us or opencti), so no RBAC
  // yet
  @Transactional(rollbackFor = Exception.class)
  public Mitigation createMitigation(TxCtx ctx, @Valid @RequestBody MitigationCreateInput input) {
    String tenantId = writeScopeResolver.tenantForWrite(ctx, null);
    Mitigation mitigation = new Mitigation();
    mitigation.setUpdateAttributes(input);
    mitigation.setAttackPatterns(
        fromIterable(attackPatternRepository.findAllById(input.getAttackPatternsIds())));
    mitigation.setTenant(new Tenant(tenantId));
    return mitigationRepository.save(mitigation);
  }

  // -- UPDATE --

  @PutMapping("/{mitigationId}")
  @Transactional
  @AccessControl(
      skipRBAC =
          true) // TODO: Mitigation API is not called anywhere yet (by us or opencti), so no RBAC
  // yet
  // TxCtx scopes the lookup and the update to the caller's tenants; a mitigation outside the scope
  // is not found, so a cross-tenant write cannot reach it. The handler does not use it directly.
  public Mitigation updateMitigation(
      TxCtx ctx,
      @NotBlank @PathVariable final String mitigationId,
      @Valid @RequestBody MitigationUpdateInput input) {
    Mitigation mitigation =
        this.mitigationRepository.findById(mitigationId).orElseThrow(ElementNotFoundException::new);
    mitigation.setUpdateAttributes(input);
    mitigation.setAttackPatterns(
        fromIterable(this.attackPatternRepository.findAllById(input.getAttackPatternsIds())));
    mitigation.setUpdatedAt(Instant.now());
    return mitigationRepository.save(mitigation);
  }

  // -- UPSERT --

  @PostMapping("/upsert")
  @AccessControl(
      skipRBAC =
          true) // TODO: Mitigation API is not called anywhere yet (by us or opencti), so no RBAC
  // yet
  @Transactional(rollbackFor = Exception.class)
  public Iterable<Mitigation> upsertMitigation(
      TxCtx ctx, @Valid @RequestBody MitigationUpsertInput input) {
    String tenantId = writeScopeResolver.tenantForWrite(ctx, null);
    List<MitigationCreateInput> mitigations = input.getMitigations();
    return new ArrayList<>(upsertMitigations(mitigations, tenantId));
  }

  private List<Mitigation> upsertMitigations(
      List<MitigationCreateInput> mitigations, String tenantId) {
    List<Mitigation> upserted = new ArrayList<>();
    mitigations.forEach(
        mitigationInput -> {
          String mitigationExternalId = mitigationInput.getExternalId();
          Optional<Mitigation> optionalMitigation =
              mitigationRepository.findByExternalId(mitigationExternalId);
          List<AttackPattern> attackPatterns =
              !mitigationInput.getAttackPatternsIds().isEmpty()
                  ? fromIterable(
                      attackPatternRepository.findAllById(mitigationInput.getAttackPatternsIds()))
                  : List.of();
          if (optionalMitigation.isEmpty()) {
            Mitigation newMitigation = new Mitigation();
            newMitigation.setStixId(mitigationInput.getStixId());
            newMitigation.setExternalId(mitigationExternalId);
            newMitigation.setAttackPatterns(attackPatterns);
            newMitigation.setName(mitigationInput.getName());
            newMitigation.setDescription(mitigationInput.getDescription());
            newMitigation.setLogSources(mitigationInput.getLogSources());
            newMitigation.setThreatHuntingTechniques(mitigationInput.getThreatHuntingTechniques());
            newMitigation.setTenant(new Tenant(tenantId));
            upserted.add(newMitigation);
          } else {
            Mitigation mitigation = optionalMitigation.get();
            mitigation.setStixId(mitigationInput.getStixId());
            mitigation.setAttackPatterns(attackPatterns);
            mitigation.setName(mitigationInput.getName());
            mitigation.setDescription(mitigationInput.getDescription());
            mitigation.setLogSources(mitigationInput.getLogSources());
            mitigation.setThreatHuntingTechniques(mitigationInput.getThreatHuntingTechniques());
            upserted.add(mitigation);
          }
        });
    return fromIterable(this.mitigationRepository.saveAll(upserted));
  }

  // -- DELETE --

  @DeleteMapping("/{mitigationId}")
  @Transactional
  @AccessControl(
      skipRBAC =
          true) // TODO: Mitigation API is not called anywhere yet (by us or opencti), so no RBAC
  // yet
  // TxCtx scopes the delete to the caller's tenants; a delete outside the scope matches no row and
  // removes nothing. The handler does not use it directly.
  public void deleteMitigation(TxCtx ctx, @PathVariable String mitigationId) {
    mitigationRepository.deleteById(mitigationId);
  }
}
