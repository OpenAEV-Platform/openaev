package io.openaev.rest.organization;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.database.specification.OrganizationSpecification.byName;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.helper.StreamHelper.iterableToSet;
import static java.time.Instant.now;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.database.model.*;
import io.openaev.database.raw.RawOrganization;
import io.openaev.database.repository.OrganizationRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.rest.atomic_testing.form.InjectResultOutput;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.organization.form.OrganizationBulkProcessingInput;
import io.openaev.rest.organization.form.OrganizationCreateInput;
import io.openaev.rest.organization.form.OrganizationUpdateInput;
import io.openaev.service.InjectSearchService;
import io.openaev.service.organization.OrganizationService;
import io.openaev.utils.FilterUtilsJpa;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class OrganizationApi extends RestBehavior {

  public static final String ORGANIZATION_URI = "/api/organizations";
  private static final String TENANT_ORGANIZATION_URI = TENANT_PREFIX + "/organizations";

  private final OrganizationRepository organizationRepository;
  private final TagRepository tagRepository;
  private final OrganizationService organizationService;
  private final InjectSearchService injectSearchService;

  @GetMapping({ORGANIZATION_URI, TENANT_ORGANIZATION_URI})
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.ORGANIZATION)
  public Iterable<RawOrganization> organizations() {
    List<RawOrganization> organizations;
    organizations = fromIterable(organizationRepository.rawAll());
    return organizations;
  }

  @PostMapping({ORGANIZATION_URI + "/search", TENANT_ORGANIZATION_URI + "/search"})
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.ORGANIZATION)
  public Page<Organization> organizations(
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    return this.organizationService.organizationPagination(searchPaginationInput);
  }

  @GetMapping({
    ORGANIZATION_URI + "/{organizationId}",
    TENANT_ORGANIZATION_URI + "/{organizationId}"
  })
  @Transactional
  @AccessControl(
      resourceId = "#organizationId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.ORGANIZATION)
  public Organization organization(@PathVariable String organizationId) {
    return organizationRepository
        .findById(organizationId)
        .orElseThrow(ElementNotFoundException::new);
  }

  /**
   * "Injects played" for the organization detail page: every inject (atomic testing or simulation
   * inject) that concerns this organization through its teams, whether they were targeted directly
   * or evidenced by the table-top expectations persisted at execution time. Resolved server-side so
   * the page does not need to load every team of the platform to build the scope.
   */
  @LogExecutionTime
  @PostMapping({
    ORGANIZATION_URI + "/{organizationId}/injects/search",
    TENANT_ORGANIZATION_URI + "/{organizationId}/injects/search"
  })
  @AccessControl(
      resourceId = "#organizationId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.ORGANIZATION)
  @Transactional(readOnly = true)
  public Page<InjectResultOutput> searchInjectsForOrganization(
      @PathVariable @NotBlank final String organizationId,
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    return injectSearchService.getPageOfInjectResultsForOrganization(
        organizationId, searchPaginationInput);
  }

  @PostMapping({ORGANIZATION_URI, TENANT_ORGANIZATION_URI})
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.ORGANIZATION)
  @Transactional(rollbackFor = Exception.class)
  public Organization createOrganization(@Valid @RequestBody OrganizationCreateInput input) {
    Organization organization = new Organization();
    organization.setUpdateAttributes(input);
    organization.setTags(iterableToSet(tagRepository.findAllById(input.getTagIds())));
    return organizationRepository.save(organization);
  }

  @PutMapping({
    ORGANIZATION_URI + "/{organizationId}",
    TENANT_ORGANIZATION_URI + "/{organizationId}"
  })
  @Transactional
  @AccessControl(
      resourceId = "#organizationId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.ORGANIZATION)
  public Organization updateOrganization(
      @PathVariable String organizationId, @Valid @RequestBody OrganizationUpdateInput input) {
    Organization organization =
        organizationRepository.findById(organizationId).orElseThrow(ElementNotFoundException::new);
    organization.setUpdateAttributes(input);
    organization.setUpdatedAt(now());
    organization.setTags(iterableToSet(tagRepository.findAllById(input.getTagIds())));
    return organizationRepository.save(organization);
  }

  @DeleteMapping({
    ORGANIZATION_URI + "/{organizationId}",
    TENANT_ORGANIZATION_URI + "/{organizationId}"
  })
  @Transactional
  @AccessControl(
      resourceId = "#organizationId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.ORGANIZATION)
  public void deleteOrganization(@PathVariable String organizationId) {
    organizationRepository.deleteById(organizationId);
  }

  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "The ids of the deleted organizations")
      })
  @Operation(
      summary = "Bulk delete organizations",
      description =
          "Deletes the organizations matching either an explicit id list"
              + " (organization_ids_to_process) or a search scope (search_pagination_input) with"
              + " optional exclusions (organization_ids_to_ignore) - exactly one of the two"
              + " selection modes must be provided. Organizations from other tenants are silently"
              + " skipped.")
  @DeleteMapping({ORGANIZATION_URI, TENANT_ORGANIZATION_URI})
  // SUPPORTS (not REQUIRED): the deletion runs in small independent chunk transactions with
  // deadlock retry; a request-wide transaction would force everything back into one transaction.
  @Transactional(propagation = Propagation.SUPPORTS)
  @AccessControl(actionPerformed = Action.DELETE, resourceType = ResourceType.ORGANIZATION)
  public List<String> bulkDeleteOrganizations(
      @RequestBody @Valid final OrganizationBulkProcessingInput input) {
    return organizationService.bulkDelete(input);
  }

  // -- OPTION --

  @GetMapping({ORGANIZATION_URI + "/options", TENANT_ORGANIZATION_URI + "/options"})
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.ORGANIZATION)
  public List<FilterUtilsJpa.Option> optionsByName(
      @RequestParam(required = false) final String searchText) {
    return fromIterable(
            this.organizationRepository.findAll(
                byName(searchText), Sort.by(Sort.Direction.ASC, "name")))
        .stream()
        .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
        .toList();
  }

  @PostMapping({ORGANIZATION_URI + "/options", TENANT_ORGANIZATION_URI + "/options"})
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.ORGANIZATION)
  public List<FilterUtilsJpa.Option> optionsById(@RequestBody final List<String> ids) {
    return fromIterable(this.organizationRepository.findAllById(ids)).stream()
        .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
        .toList();
  }
}
