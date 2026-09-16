package io.openaev.service.organization;

import static io.openaev.database.specification.OrganizationSpecification.byName;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;
import static io.openaev.utils.pagination.SearchUtilsJpa.computeSearchJpa;
import static java.time.Instant.now;

import io.openaev.context.TenantContext;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Organization;
import io.openaev.database.model.Tag;
import io.openaev.database.model.Tenant;
import io.openaev.database.raw.RawOrganization;
import io.openaev.database.repository.OrganizationRepository;
import io.openaev.database.specification.SpecificationUtils;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.organization.form.OrganizationBulkProcessingInput;
import io.openaev.rest.organization.form.OrganizationCreateInput;
import io.openaev.rest.organization.form.OrganizationUpdateInput;
import io.openaev.rest.tag.TagService;
import io.openaev.service.utils.BulkDeleteExecutor;
import io.openaev.utils.FilterUtilsJpa;
import io.openaev.utils.TxCtxScopeUtils;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.validation.constraints.NotNull;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

@Service
@RequiredArgsConstructor
public class OrganizationService {

  private final OrganizationRepository organizationRepository;
  private final BulkDeleteExecutor bulkDeleteExecutor;
  private final TagService tagService;

  /** Lists organizations in the legacy tenant scope, bounded by the caller's authorized scope. */
  @Transactional(readOnly = true)
  public List<RawOrganization> organizations(TxCtx ctx) {
    Set<String> tenantIds = TxCtxScopeUtils.tenantIdsFromHTTPCtx(ctx);
    return tenantIds.isEmpty() ? List.of() : organizationRepository.rawAll(tenantIds);
  }

  /** Resolves an organization only when it belongs to the authorized request scope. */
  @Transactional(readOnly = true)
  public Organization findById(TxCtx ctx, String organizationId) {
    return findAccessibleById(ctx, organizationId);
  }

  /** Searches within the intersection of the legacy tenant filter and the authorized scope. */
  @Transactional(readOnly = true)
  public Page<Organization> organizationPagination(
      TxCtx ctx, @NotNull SearchPaginationInput searchPaginationInput) {
    Specification<Organization> scope = inScope(ctx);
    return buildPaginationJPA(
        (specification, pageable) ->
            organizationRepository.findAll(scope.and(specification), pageable),
        searchPaginationInput,
        Organization.class);
  }

  /** Creates an organization in the tenant explicitly resolved by the API write-scope resolver. */
  @Transactional(rollbackFor = Exception.class)
  public Organization createOrganization(
      TxCtx ctx, OrganizationCreateInput input, String tenantId) {
    Set<Tag> tags = resolveTags(input.getTagIds(), tenantId);
    Organization organization = new Organization();
    organization.setUpdateAttributes(input);
    organization.setTenant(new Tenant(tenantId));
    organization.setTags(tags);
    return organizationRepository.save(organization);
  }

  /** Checks ownership and associations before changing any managed organization attributes. */
  @Transactional(rollbackFor = Exception.class)
  public Organization updateOrganization(
      TxCtx ctx, String organizationId, OrganizationUpdateInput input) {
    Organization organization = findAccessibleById(ctx, organizationId);
    Set<Tag> tags = resolveTags(input.getTagIds(), organization.getTenant().getId());
    organization.setUpdateAttributes(input);
    organization.setUpdatedAt(now());
    organization.setTags(tags);
    return organizationRepository.save(organization);
  }

  /** Deletes an authorized organization through the ORM to preserve lifecycle events. */
  @Transactional(rollbackFor = Exception.class)
  public void deleteOrganization(TxCtx ctx, String organizationId) {
    organizationRepository.delete(findAccessibleById(ctx, organizationId));
  }

  /**
   * Finds an organization by name within the current tenant, creating it if missing. Single source
   * of truth for attributing connector-authored arsenal content (collector payloads and injector
   * contracts) to a publisher organization named after the declared author.
   *
   * @return the resolved organization, or {@code null} when {@code name} is blank
   */
  public Organization findOrCreateByName(final String name) {
    if (name == null || name.isBlank()) {
      return null;
    }
    return organizationRepository.findByNameIgnoreCase(name).stream()
        .findFirst()
        .orElseGet(
            () -> {
              Organization organization = new Organization();
              organization.setName(name);
              organization.setTenant(new Tenant(TenantContext.getCurrentTenant()));
              return organizationRepository.save(organization);
            });
  }

  /**
   * Bulk delete of organizations, either from an explicit list of ids or from a search input
   * (select all), mirroring the teams/players bulk deletes.
   *
   * <p>Not transactional as a whole: the deletion scope is resolved in a short transaction, then
   * organizations are deleted in small independent chunks (with deadlock retry) tracked as a
   * massive operation, so per-entity stream events are suppressed in favor of aggregated progress
   * events.
   *
   * @param input the bulk processing input
   * @return the ids of the deleted organizations
   */
  public List<String> bulkDelete(
      final TxCtx ctx, @NotNull final OrganizationBulkProcessingInput input) {
    if ((CollectionUtils.isEmpty(input.getOrganizationIdsToProcess())
            && input.getSearchPaginationInput() == null)
        || (!CollectionUtils.isEmpty(input.getOrganizationIdsToProcess())
            && input.getSearchPaginationInput() != null)) {
      throw new BadRequestException(
          "Either organization_ids_to_process or search_pagination_input must be provided, and not both at the same time");
    }
    Specification<Organization> scope = inScope(ctx);
    List<String> organizationIdsToDelete =
        bulkDeleteExecutor.resolveInTransaction(
            ctx,
            () -> {
              Specification<Organization> specification;
              if (input.getSearchPaginationInput() != null) {
                // Same specification chain as the list search (filter group + text search), so the
                // deletion scope matches exactly what the user sees in the list.
                specification =
                    FilterUtilsJpa.<Organization>computeFilterGroupJpa(
                            input.getSearchPaginationInput().getFilterGroup())
                        .and(computeSearchJpa(input.getSearchPaginationInput().getTextSearch()));
              } else {
                specification = SpecificationUtils.hasIdIn(input.getOrganizationIdsToProcess());
              }
              if (!CollectionUtils.isEmpty(input.getOrganizationIdsToIgnore())) {
                List<String> idsToIgnore = input.getOrganizationIdsToIgnore();
                specification =
                    specification.and((root, query, cb) -> cb.not(root.get("id").in(idsToIgnore)));
              }
              return organizationRepository.findAll(scope.and(specification)).stream()
                  .map(Organization::getId)
                  .toList();
            });
    return bulkDeleteExecutor.deleteInChunks(
        ctx,
        "organizations",
        organizationIdsToDelete,
        chunk ->
            organizationRepository.deleteAll(
                organizationRepository.findAll(scope.and(SpecificationUtils.hasIdIn(chunk)))));
  }

  /** Resolves autocomplete labels without exposing organizations outside the request scope. */
  @Transactional(readOnly = true)
  public List<FilterUtilsJpa.Option> optionsByName(TxCtx ctx, String searchText) {
    return organizationRepository
        .findAll(inScope(ctx).and(byName(searchText)), Sort.by(Sort.Direction.ASC, "name"))
        .stream()
        .map(
            organization -> new FilterUtilsJpa.Option(organization.getId(), organization.getName()))
        .toList();
  }

  /** Resolves supplied option identifiers only within the authorized legacy tenant scope. */
  @Transactional(readOnly = true)
  public List<FilterUtilsJpa.Option> optionsById(TxCtx ctx, List<String> ids) {
    return organizationRepository
        .findAll(inScope(ctx).and(SpecificationUtils.hasIdIn(ids)))
        .stream()
        .map(
            organization -> new FilterUtilsJpa.Option(organization.getId(), organization.getName()))
        .toList();
  }

  private Organization findAccessibleById(TxCtx ctx, String organizationId) {
    Set<String> tenantIds = TxCtxScopeUtils.tenantIdsFromHTTPCtx(ctx);
    if (tenantIds.isEmpty()) {
      throw new ElementNotFoundException();
    }
    // Hibernate's v1 filter does not apply to primary-key loads, including cached entities.
    return organizationRepository
        .findById(organizationId)
        .filter(
            organization ->
                organization.getTenant() != null
                    && tenantIds.contains(organization.getTenant().getId()))
        .orElseThrow(ElementNotFoundException::new);
  }

  private Specification<Organization> inScope(TxCtx ctx) {
    Set<String> tenantIds = TxCtxScopeUtils.tenantIdsFromHTTPCtx(ctx);
    return (root, query, cb) ->
        tenantIds.isEmpty() ? cb.disjunction() : root.get("tenant").get("id").in(tenantIds);
  }

  private Set<Tag> resolveTags(List<String> tagIds, String tenantId) {
    if (tagIds == null) {
      throw new BadRequestException("organization_tags must not be null");
    }
    Set<Tag> tags = tagService.tagSet(tagIds);
    if (tags.size() != new HashSet<>(tagIds).size()
        || tags.stream()
            .anyMatch(
                tag -> tag.getTenant() == null || !tenantId.equals(tag.getTenant().getId()))) {
      throw new ElementNotFoundException();
    }
    return tags;
  }
}
