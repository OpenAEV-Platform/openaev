package io.openbas.service.organization;

import static io.openbas.config.SessionHelper.currentUser;
import static io.openbas.database.specification.OrganizationSpecification.findGrantedFor;
import static io.openbas.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.openbas.database.model.Organization;
import io.openbas.database.model.User;
import io.openbas.database.repository.OrganizationRepository;
import io.openbas.database.repository.UserRepository;
import io.openbas.rest.exception.ElementNotFoundException;
import io.openbas.service.UserService;
import io.openbas.utils.pagination.SearchPaginationInput;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrganizationService {

  private final OrganizationRepository organizationRepository;

  private final UserService userService;
  private final UserRepository userRepository;

  public Page<Organization> organizationPagination(
      @NotNull SearchPaginationInput searchPaginationInput) {
    User currentUser = userService.currentUser();
    if (currentUser.isAdminOrBypass()) {
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

  public void checkOrganizationAccess(String organizationId) {
    if (organizationId != null) {
      User currentUser = userService.currentUser();
      if (!currentUser.isAdminOrBypass()) {
        User local =
            userRepository
                .findById(currentUser.getId())
                .orElseThrow(() -> new ElementNotFoundException("Current user not found"));
        List<String> localOrganizationIds =
            local.getGroups().stream()
                .flatMap(group -> group.getOrganizations().stream())
                .map(Organization::getId)
                .toList();
        if (!localOrganizationIds.contains(organizationId)) {
          throw new UnsupportedOperationException("User is restricted");
        }
      }
    }
  }
}
