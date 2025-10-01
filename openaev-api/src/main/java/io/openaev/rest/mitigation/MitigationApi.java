package io.openaev.rest.mitigation;

import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.openaev.aop.RBAC;
import io.openaev.database.model.AttackPattern;
import io.openaev.database.model.Mitigation;
import io.openaev.database.repository.AttackPatternRepository;
import io.openaev.database.repository.MitigationRepository;
import io.openaev.database.specification.AttackPatternSpecification;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.mitigation.form.MitigationCreateInput;
import io.openaev.rest.mitigation.form.MitigationUpdateInput;
import io.openaev.rest.mitigation.form.MitigationUpsertInput;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.bind.annotation.*;

@RestController
public class MitigationApi extends RestBehavior {

  private MitigationRepository mitigationRepository;

  private AttackPatternRepository attackPatternRepository;

  @Autowired
  public void setMitigationRepository(MitigationRepository mitigationRepository) {
    this.mitigationRepository = mitigationRepository;
  }

  @Autowired
  public void setAttackPatternRepository(AttackPatternRepository attackPatternRepository) {
    this.attackPatternRepository = attackPatternRepository;
  }

  @GetMapping("/api/mitigations")
  @RBAC(
      skipRBAC =
          true) // TODO: Mitigation API is not called anywhere yet (by us or opencti), so no RBAC
  // yet
  public Iterable<Mitigation> mitigations() {
    return mitigationRepository.findAll();
  }

  @PostMapping("/api/mitigations/search")
  @RBAC(
      skipRBAC =
          true) // TODO: Mitigation API is not called anywhere yet (by us or opencti), so no RBAC
  // yet
  public Page<Mitigation> mitigations(
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    return buildPaginationJPA(
        (Specification<Mitigation> specification, Pageable pageable) ->
            this.mitigationRepository.findAll(specification, pageable),
        searchPaginationInput,
        Mitigation.class);
  }

  @GetMapping("/api/mitigations/{mitigationId}")
  @RBAC(
      skipRBAC =
          true) // TODO: Mitigation API is not called anywhere yet (by us or opencti), so no RBAC
  // yet
  public Mitigation mitigation(@PathVariable String mitigationId) {
    return mitigationRepository.findById(mitigationId).orElseThrow(ElementNotFoundException::new);
  }

  @PostMapping("/api/mitigations")
  @RBAC(
      skipRBAC =
          true) // TODO: Mitigation API is not called anywhere yet (by us or opencti), so no RBAC
  // yet
  @Transactional(rollbackOn = Exception.class)
  public Mitigation createMitigation(@Valid @RequestBody MitigationCreateInput input) {
    Mitigation mitigation = new Mitigation();
    mitigation.setUpdateAttributes(input);
    mitigation.setAttackPatterns(
        fromIterable(attackPatternRepository.findAllById(input.getAttackPatternsIds())));
    return mitigationRepository.save(mitigation);
  }

  @GetMapping("/api/mitigations/{mitigationId}/attack_patterns")
  @RBAC(
      skipRBAC =
          true) // TODO: Mitigation API is not called anywhere yet (by us or opencti), so no RBAC
  // yet
  public Iterable<AttackPattern> injectorContracts(@PathVariable String mitigationId) {
    mitigationRepository.findById(mitigationId).orElseThrow(ElementNotFoundException::new);
    return attackPatternRepository.findAll(
        AttackPatternSpecification.fromAttackPattern(mitigationId));
  }

  @PutMapping("/api/mitigations/{mitigationId}")
  @RBAC(
      skipRBAC =
          true) // TODO: Mitigation API is not called anywhere yet (by us or opencti), so no RBAC
  // yet
  public Mitigation updateMitigation(
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

  private List<Mitigation> upsertMitigations(List<MitigationCreateInput> mitigations) {
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

  @PostMapping("/api/mitigations/upsert")
  @RBAC(
      skipRBAC =
          true) // TODO: Mitigation API is not called anywhere yet (by us or opencti), so no RBAC
  // yet
  @Transactional(rollbackOn = Exception.class)
  public Iterable<Mitigation> upsertMitigation(@Valid @RequestBody MitigationUpsertInput input) {
    List<MitigationCreateInput> mitigations = input.getMitigations();
    return new ArrayList<>(upsertMitigations(mitigations));
  }

  @DeleteMapping("/api/mitigations/{mitigationId}")
  @RBAC(
      skipRBAC =
          true) // TODO: Mitigation API is not called anywhere yet (by us or opencti), so no RBAC
  // yet
  public void deleteMitigation(@PathVariable String mitigationId) {
    mitigationRepository.deleteById(mitigationId);
  }
}
