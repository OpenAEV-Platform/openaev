package io.openaev.rest.user;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.rest.user.TenantUserApi.TENANT_USER_URI;
import static io.openaev.rest.user.TenantUserApi.USER_URI;

import io.openaev.aop.AccessControl;
import io.openaev.aop.UserRoleDescription;
import io.openaev.api.users.dto.UserInput;
import io.openaev.api.users.dto.UserOutput;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.database.raw.RawUser;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.service.tenants.TenantUserService;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@UserRoleDescription
@Tag(
    name = "Users management",
    description = "Endpoints to manage users",
    externalDocs =
        @ExternalDocumentation(
            description = "Documentation about users",
            url = "https://docs.openaev.io/latest/administration/users/"))
@RequestMapping({USER_URI, TENANT_USER_URI})
public class TenantUserApi extends RestBehavior {

  public static final String USER_URI = "/api/users";
  public static final String TENANT_USER_URI = TENANT_PREFIX + "/users";
  private final TenantUserService tenantUserService;

  // -- CREATE --

  @Operation(summary = "Create or attach a user to the current tenant")
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.USER)
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public UserOutput create(@Valid @RequestBody UserInput input) {
    return tenantUserService.createOrAttach(input);
  }

  // -- READ --

  @Operation(summary = "Get tenant user by ID")
  @AccessControl(
      resourceId = "#userId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.USER)
  @GetMapping("/{userId}")
  public UserOutput findById(@PathVariable String userId) {
    return tenantUserService.user(userId);
  }

  @Operation(summary = "Find users by IDs")
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.USER)
  @PostMapping("/find")
  public List<UserOutput> find(@RequestBody @Valid @NotNull final List<String> userIds) {
    return tenantUserService.find(userIds);
  }

  @Operation(summary = "List users")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of users")})
  @GetMapping
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.USER)
  public List<RawUser> users() {
    return tenantUserService.users();
  }

  // -- SEARCH --

  @Operation(summary = "Search tenant users")
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.USER)
  @PostMapping("/search")
  public Page<UserOutput> search(
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    return tenantUserService.search(searchPaginationInput);
  }

  // -- UPDATE --

  @Operation(summary = "Update a tenant user")
  @AccessControl(
      resourceId = "#userId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.USER)
  @PutMapping("/{userId}")
  public UserOutput update(@PathVariable String userId, @Valid @RequestBody UserInput input) {
    return tenantUserService.update(userId, input);
  }

  // -- DELETE --

  @Operation(summary = "Detach a user from the current tenant")
  @AccessControl(
      resourceId = "#userId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.USER)
  @DeleteMapping("/{userId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String userId) {
    tenantUserService.detach(userId);
  }
}
