package io.openaev.service.organization;

import static io.openaev.database.specification.OrganizationSpecification.byName;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;
import static io.openaev.utils.pagination.SearchUtilsJpa.computeSearchJpa;
import static java.time.Instant.now;

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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Service
@RequiredArgsConstructor
public class OrganizationService {

  private final OrganizationRepository organizationRepository;
  private final BulkDeleteExecutor bulkDeleteExecutor;
  private final TagService tagService;

  /**
   * Returns a list of raw organizations based on the tenant context.
   *
   * @param ctx the transaction context containing tenant information
   * @return a list of raw organizations for the tenants in the context
   */
  public List<RawOrganization> organizations(TxCtx ctx) {
    Set<String> tenantIds = TxCtxScopeUtils.tenantIdsFromHTTPCtx(ctx);
    return tenantIds.isEmpty() ? List.of() : organizationRepository.rawAll(tenantIds);
  }

  /**
   * Finds an organization by id, throwing an exception if not found.
   *
   * @param organizationId the id of the organization to find
   * @return the found organization
   */
  public Organization findById(String organizationId) {
    return organizationRepository
        .findById(organizationId)
        .orElseThrow(ElementNotFoundException::new);
  }

  /**
   * Finds organizations with pagination based on the provided search and pagination input.
   *
   * @param searchPaginationInput the input containing search and pagination parameters
   * @return a page of organizations matching the search criteria
   */
  public Page<Organization> organizationPagination(
      @NotNull SearchPaginationInput searchPaginationInput) {
    return buildPaginationJPA(
        organizationRepository::findAll, searchPaginationInput, Organization.class);
  }

  /**
   * Creates a new organization with the provided input and tenant id.
   *
   * @param input the input containing organization details
   * @param tenantId the id of the tenant to which the organization belongs
   * @return the created organization
   */
  public Organization createOrganization(OrganizationCreateInput input, @NotBlank String tenantId) {
    Set<Tag> tags = resolveTags(input.getTagIds(), tenantId);
    Organization organization = new Organization();
    organization.setUpdateAttributes(input);
    organization.setTenant(new Tenant(tenantId));
    organization.setTags(tags);
    return organizationRepository.save(organization);
  }

  /**
   * Updates an existing organization with the provided input.
   *
   * @param organizationId the id of the organization to update
   * @param input the input containing updated organization details
   * @return the updated organization
   */
  public Organization updateOrganization(String organizationId, OrganizationUpdateInput input) {
    Organization organization = findById(organizationId);
    Set<Tag> tags = resolveTags(input.getTagIds(), organization.getTenant().getId());
    organization.setUpdateAttributes(input);
    organization.setUpdatedAt(now());
    organization.setTags(tags);
    return organizationRepository.save(organization);
  }

  /**
   * Deletes an organization by its id.
   *
   * @param organizationId the id of the organization to delete
   */
  public void deleteOrganization(String organizationId) {
    organizationRepository.delete(findById(organizationId));
  }

  /**
   * Finds an organization by name within the specified tenant, creating it if missing. . Single
   * source of truth for attributing connector-authored arsenal content (collector payloads and
   * injector contracts) to a publisher organization named after the declared author.
   *
   * @param name the name of the organization
   * @param tenantId the id of the tenant
   * @return the resolved organization, or {@code null} when {@code name} is blank
   */
  public Organization findOrCreateByName(final String name, @NotBlank String tenantId) {
    if (name == null || name.isBlank()) {
      return null;
    }
    return organizationRepository.findByNameIgnoreCaseAndTenantId(name, tenantId).stream()
        .findFirst()
        .orElseGet(
            () -> {
              Organization organization = new Organization();
              organization.setName(name);
              organization.setTenant(new Tenant(tenantId));
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
    Specification<Organization> scope = inTenantScope(ctx);
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

  /**
   * Returns a list of organization options based on the provided search text.
   *
   * @param searchText the text to search for in organization names
   * @return a list of organization options matching the search text
   */
  public List<FilterUtilsJpa.Option> optionsByName(String searchText) {
    return organizationRepository
        .findAll(byName(searchText), Sort.by(Sort.Direction.ASC, "name"))
        .stream()
        .map(
            organization -> new FilterUtilsJpa.Option(organization.getId(), organization.getName()))
        .toList();
  }

  /**
   * Returns a list of organization options based on the provided organization IDs.
   *
   * @param ids the IDs of the organizations to retrieve options for
   * @return a list of organization options matching the provided IDs
   */
  public List<FilterUtilsJpa.Option> optionsById(List<String> ids) {
    return organizationRepository.findAll(SpecificationUtils.hasIdIn(ids)).stream()
        .map(
            organization -> new FilterUtilsJpa.Option(organization.getId(), organization.getName()))
        .toList();
  }

  private Specification<Organization> inTenantScope(TxCtx ctx) {
    Set<String> tenantIds = TxCtxScopeUtils.tenantIdsFromHTTPCtx(ctx);
    return (root, query, cb) ->
        tenantIds.isEmpty() ? cb.disjunction() : root.get("tenant").get("id").in(tenantIds);
  }

  private Set<Tag> resolveTags(List<String> tagIds, @NotNull String tenantId) {
    if (tagIds == null) {
      throw new BadRequestException("organization_tags must not be null");
    }
    Set<Tag> tags = tagService.tagSet(tagIds);
    if (tags.size() != new HashSet<>(tagIds).size()
        || tags.stream().anyMatch(tag -> !tenantId.equals(tag.getTenant().getId()))) {
      throw new ElementNotFoundException();
    }
    return tags;
  }
}
