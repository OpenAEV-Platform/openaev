package io.openaev.api.markings;

import static io.openaev.utils.pagination.CriteriaBuilderPagination.paginate;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationCriteriaBuilder;

import io.openaev.api.markings.form.MarkingDefinitionInput;
import io.openaev.api.markings.response.MarkingDefinitionOutput;
import io.openaev.config.cache.MarkingClearanceCacheManager;
import io.openaev.database.model.MarkingDefinition;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.MarkingDefinitionRepository;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD over the marking catalogue.
 *
 * <p>Deliberately skimmed for the marking PoC (step 2.1): no clearance checks on the definitions
 * themselves, and no assignment endpoints. Tenant isolation is entirely the v2 statement
 * inspector's job, so nothing here mentions {@code TenantContext}.
 */
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class MarkingDefinitionService {

  private final MarkingDefinitionRepository markingDefinitionRepository;
  private final MarkingClearanceCacheManager markingClearanceCacheManager;
  @PersistenceContext private EntityManager entityManager;

  // -- CREATE --

  /**
   * The tenant is resolved in the API layer and passed in, per the multi-tenancy convention: this
   * service never touches {@code TenantContext}.
   */
  public MarkingDefinition create(
      @NotBlank String tenantId, @NotNull MarkingDefinitionInput input) {
    assertNameIsFree(input.name(), null);
    MarkingDefinition marking = MarkingDefinitionMapper.apply(new MarkingDefinition(), input);
    marking.setTenant(new Tenant(tenantId));
    return markingDefinitionRepository.save(marking);
  }

  // -- READ --

  @Transactional(readOnly = true)
  public MarkingDefinition findById(@NotBlank String id) {
    return getOrThrow(id);
  }

  @Transactional(readOnly = true)
  public Page<MarkingDefinitionOutput> search(@NotNull SearchPaginationInput input) {
    return buildPaginationCriteriaBuilder(
        (spec, specCount, pageable) ->
            paginate(
                entityManager,
                MarkingDefinition.class,
                spec,
                specCount,
                pageable,
                MarkingDefinitionQueryHelper::select,
                MarkingDefinitionQueryHelper::execution),
        input,
        MarkingDefinition.class);
  }

  // -- UPDATE --

  /**
   * Evicts every cached clearance, because {@code order} and {@code type} are the inputs the
   * resolver expands a grant against — not just labels. Raising a marking's order pushes it above
   * clearances that previously covered it, so a stale entry keeps granting a marking the new data
   * no longer justifies: fail-open. Blunt because the affected set is "everyone holding a grant of
   * this type", and computing it is itself a query (see {@link
   * MarkingClearanceCacheManager#evictAll}).
   */
  public MarkingDefinition update(@NotBlank String id, @NotNull MarkingDefinitionInput input) {
    MarkingDefinition existing = getOrThrow(id);
    assertNameIsFree(input.name(), id);
    MarkingDefinition saved =
        markingDefinitionRepository.save(MarkingDefinitionMapper.apply(existing, input));
    markingClearanceCacheManager.evictAll();
    return saved;
  }

  // -- DELETE --

  /**
   * Hard delete. {@code groups_markings} rows cascade, so no group keeps a dangling grant.
   *
   * <p>Rows that already carry this marking in their {@code marking_ids} array are <b>not</b>
   * scrubbed here: there is no foreign key to cascade through, and no table is marking-activated
   * yet. That scrub is part of activation (design §6.8) and lands with step 3.
   */
  public void delete(@NotBlank String id) {
    markingDefinitionRepository.delete(getOrThrow(id));
    // The cascade removes the grants, but not the clearances already derived from them.
    markingClearanceCacheManager.evictAll();
  }

  // -- PRIVATE --

  /**
   * Non-transactional lookup for internal callers. Calling the public {@code findById} from within
   * this class would be a self-invocation: the Spring proxy is bypassed, so neither the transaction
   * nor the tenant scope would apply.
   */
  private MarkingDefinition getOrThrow(String id) {
    return markingDefinitionRepository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Marking definition not found: " + id));
  }

  /** Mirrors the {@code (marking_name, tenant_id)} unique index with a readable error. */
  private void assertNameIsFree(String name, String allowedId) {
    boolean clash =
        markingDefinitionRepository.findAllByName(name).stream()
            .anyMatch(existing -> !existing.getId().equals(allowedId));
    if (clash) {
      throw new BadRequestException("Marking name already used: " + name);
    }
  }

  @Transactional(readOnly = true)
  public List<MarkingDefinition> findAllByType(@NotBlank String type) {
    return markingDefinitionRepository.findAllByTypeOrderByOrderAsc(type);
  }
}
