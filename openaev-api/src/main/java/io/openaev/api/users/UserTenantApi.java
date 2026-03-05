package io.openaev.api.users;

import io.openaev.aop.AccessControl;
import io.openaev.api.users.dto.UserInput;
import io.openaev.api.users.dto.UserOutput;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.User;
import io.openaev.database.specification.UserSpecification;
import io.openaev.rest.user.service.UserCriteriaBuilderService;
import io.openaev.service.UserService;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import static io.openaev.api.users.dto.UserMapper.toOutput;

/**
 * User management API scoped to a specific tenant.
 */
@RestController
@RequestMapping("/api/tenants/{tenantId}/users")
@RequiredArgsConstructor
public class UserTenantApi {

  private final UserService userService;
  private final UserCriteriaBuilderService userCriteriaBuilderService;

  // -- CREATE --

  @Operation(
      summary = "Create a user in a tenant",
      description = "Creates a new user and links it to the tenant")
  @AccessControl(
      actionPerformed = Action.CREATE,
      resourceType = ResourceType.USER,
      isEnterpriseEdition = true)
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public UserOutput create(@Valid @RequestBody UserInput input) {
    return toOutput(userService.createUserInTenant(input, TenantContext.getCurrentTenant()));
  }

  // -- READ --

  @Operation(
      summary = "Get user by ID in a tenant",
      description = "Retrieves a user that belongs to the tenant")
  @AccessControl(
      resourceId = "#userId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.USER,
      isEnterpriseEdition = true)
  @GetMapping("/{userId}")
  public UserOutput getById(@PathVariable String userId) {
    return userCriteriaBuilderService.findById(userId, tenantSpec());
  }

  // -- SEARCH --

  @Operation(
      summary = "Search users in a tenant",
      description = "Search users with pagination and filtering, scoped to the tenant")
  @AccessControl(
      actionPerformed = Action.READ,
      resourceType = ResourceType.USER,
      isEnterpriseEdition = true)
  @PostMapping("/search")
  public Page<UserOutput> search(
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    return userCriteriaBuilderService.userPagination(searchPaginationInput, tenantSpec());
  }

  // -- UPDATE --

  @Operation(summary = "Update a user in a tenant", description = "Updates a user that belongs to the tenant")
  @AccessControl(
      resourceId = "#userId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.USER,
      isEnterpriseEdition = true)
  @PutMapping("/{userId}")
  public UserOutput update(@PathVariable String userId, @Valid @RequestBody UserInput input) {
    return toOutput(userService.update(userId, input));
  }

  // -- DELETE --

  @Operation(
      summary = "Remove a user from a tenant",
      description = "Removes the user-tenant link (does not delete the user)")
  @AccessControl(
      resourceId = "#userId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.USER,
      isEnterpriseEdition = true)
  @DeleteMapping("/{userId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String userId) {
    userService.removeUserFromTenant(userId, TenantContext.getCurrentTenant());
  }

  // -- PRIVATE --

  private static Specification<User> tenantSpec() {
    return UserSpecification.inTenant(TenantContext.getCurrentTenant());
  }
}
