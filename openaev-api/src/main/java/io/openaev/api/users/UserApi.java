package io.openaev.api.users;

import io.openaev.aop.AccessControl;
import io.openaev.api.users.dto.UserInput;
import io.openaev.api.users.dto.UserOutput;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.user.service.UserCriteriaBuilderService;
import io.openaev.service.UserService;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import static io.openaev.api.users.dto.UserMapper.toOutput;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserApi {

  private final UserService userService;
  private final UserCriteriaBuilderService userCriteriaBuilderService;

  // -- CREATE --

  @Operation(
      summary = "Create a user",
      description = "Creates a new user (Enterprise edition only)")
  @AccessControl(
      actionPerformed = Action.CREATE,
      resourceType = ResourceType.USER,
      isEnterpriseEdition = true)
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public UserOutput create(@Valid @RequestBody UserInput input) {
    return toOutput(userService.createUser(input));
  }

  // -- READ --

  @Operation(
      summary = "Get user by ID",
      description = "Retrieves a user by its unique identifier")
  @AccessControl(
      resourceId = "#userId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.USER,
      isEnterpriseEdition = true)
  @GetMapping("/{userId}")
  public UserOutput getById(@PathVariable String userId) {
    return userCriteriaBuilderService.findById(userId);
  }

  // -- SEARCH --

  @Operation(
      summary = "Search users",
      description = "Search users with pagination and filtering")
  @AccessControl(
      actionPerformed = Action.READ,
      resourceType = ResourceType.USER,
      isEnterpriseEdition = true)
  @PostMapping("/search")
  public Page<UserOutput> search(
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    return userCriteriaBuilderService.userPagination(searchPaginationInput);
  }

  // -- UPDATE --

  @Operation(summary = "Update a user", description = "Updates an existing user")
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

  @Operation(summary = "Delete a user", description = "Deletes a user by its ID")
  @AccessControl(
      resourceId = "#userId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.USER,
      isEnterpriseEdition = true)
  @DeleteMapping("/{userId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String userId) {
    userService.delete(userId);
  }
}
