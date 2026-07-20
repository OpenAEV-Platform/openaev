package io.openaev.service.organization;

import static io.openaev.database.specification.OrganizationSpecification.findGrantedFor;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.openaev.context.TenantContext;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Organization;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.OrganizationRepository;
import io.openaev.service.UserService;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrganizationService {

  private final OrganizationRepository organizationRepository;

  private final UserService userService;

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

  public Page<Organization> organizationPagination(
      @NotNull SearchPaginationInput searchPaginationInput) {
    User currentUser = userService.currentUser();
    if (currentUser.isAdminOrBypass()
        || currentUser.getCapabilities().contains(Capability.ACCESS_PLATFORM_SETTINGS)) {
      return buildPaginationJPA(
          this.organizationRepository::findAll, searchPaginationInput, Organization.class);
    } else {
      return buildPaginationJPA(
          (Specification<Organization> specification, Pageable pageable) ->
              this.organizationRepository.findAll(
                  findGrantedFor(currentUser.getId()).and(specification), pageable),
          searchPaginationInput,
          Organization.class);
    }
  }
}
