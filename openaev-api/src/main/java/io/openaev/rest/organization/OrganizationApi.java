package io.openaev.rest.organization;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.database.specification.OrganizationSpecification.byName;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.helper.StreamHelper.iterableToSet;
import static java.time.Instant.now;

import io.openaev.aop.AccessControl;
import io.openaev.database.model.*;
import io.openaev.database.raw.RawOrganization;
import io.openaev.database.repository.OrganizationRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.organization.form.OrganizationBulkProcessingInput;
import io.openaev.rest.organization.form.OrganizationCreateInput;
import io.openaev.rest.organization.form.OrganizationUpdateInput;
import io.openaev.service.organization.OrganizationService;
import io.openaev.utils.FilterUtilsJpa;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
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
          "Deletes the organizations matching the given ids (organization_ids). Organizations from other tenants are silently skipped.")
  @DeleteMapping({ORGANIZATION_URI, TENANT_ORGANIZATION_URI})
  @Transactional(rollbackFor = Exception.class)
  @AccessControl(actionPerformed = Action.DELETE, resourceType = ResourceType.ORGANIZATION)
  public List<String> bulkDeleteOrganizations(
      @RequestBody @Valid final OrganizationBulkProcessingInput input) {
    // findAllById goes through a criteria query, so the Hibernate tenant filter applies and
    // organizations from other tenants are silently skipped.
    List<Organization> organizations =
        fromIterable(organizationRepository.findAllById(input.getOrganizationIds()));
    List<String> deletedIds = organizations.stream().map(Organization::getId).toList();
    organizationRepository.deleteAll(organizations);
    return deletedIds;
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
