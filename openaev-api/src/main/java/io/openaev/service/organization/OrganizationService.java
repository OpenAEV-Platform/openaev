package io.openaev.service.organization;

import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;
import static io.openaev.utils.pagination.SearchUtilsJpa.computeSearchJpa;

import io.openaev.context.TenantContext;
import io.openaev.database.model.Organization;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.OrganizationRepository;
import io.openaev.database.specification.SpecificationUtils;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.organization.form.OrganizationBulkProcessingInput;
import io.openaev.service.utils.BulkDeleteExecutor;
import io.openaev.utils.FilterUtilsJpa;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Service
@RequiredArgsConstructor
public class OrganizationService {

  private final OrganizationRepository organizationRepository;

  private final BulkDeleteExecutor bulkDeleteExecutor;

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
  public List<String> bulkDelete(@NotNull final OrganizationBulkProcessingInput input) {
    if ((CollectionUtils.isEmpty(input.getOrganizationIdsToProcess())
            && input.getSearchPaginationInput() == null)
        || (!CollectionUtils.isEmpty(input.getOrganizationIdsToProcess())
            && input.getSearchPaginationInput() != null)) {
      throw new BadRequestException(
          "Either organization_ids_to_process or search_pagination_input must be provided, and not both at the same time");
    }
    List<String> organizationIdsToDelete =
        bulkDeleteExecutor.resolveInTransaction(
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
              return organizationRepository.findAll(specification).stream()
                  .map(Organization::getId)
                  .toList();
            });
    return bulkDeleteExecutor.deleteInChunks(
        "organizations",
        organizationIdsToDelete,
        chunk -> organizationRepository.deleteAll(organizationRepository.findAllById(chunk)));
  }

  public Page<Organization> organizationPagination(
      @NotNull SearchPaginationInput searchPaginationInput) {
    // Visibility is capability-gated at the API layer (@AccessControl SEARCH) and tenant-scoped by
    // the Hibernate tenant filter, like every other organization endpoint (raw list, options,
    // single read). The former per-group grant scoping joined Organization.groups, a mapping
    // removed along with the groups_organizations table (V4_38): keeping it made every non-admin
    // search fail with "Could not resolve attribute 'groups'".
    return buildPaginationJPA(
        this.organizationRepository::findAll, searchPaginationInput, Organization.class);
  }
}
