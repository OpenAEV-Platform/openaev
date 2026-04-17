package io.openaev.api.platform.users;

import static io.openaev.api.users.dto.UserMapper.toOutput;

import io.openaev.aop.AccessControl;
import io.openaev.api.users.dto.UserInput;
import io.openaev.api.users.dto.UserMapper;
import io.openaev.api.users.dto.UserOutput;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.service.UserService;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/platform-users")
@RequiredArgsConstructor
public class PlatformUserApi {

  private final UserService userService;

  // -- CREATE --

  @Operation(summary = "Create a platform user")
  @AccessControl(
      actionPerformed = Action.CREATE,
      resourceType = ResourceType.PLATFORM_USER,
      isEnterpriseEdition = true)
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public UserOutput create(@Valid @RequestBody UserInput input) {
    return toOutput(userService.createUser(input));
  }

  // -- READ --

  @Operation(summary = "Get platform user by ID")
  @AccessControl(
      resourceId = "#userId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.PLATFORM_USER,
      isEnterpriseEdition = true)
  @GetMapping("/{userId}")
  public UserOutput findById(@PathVariable String userId) {
    return toOutput(userService.user(userId));
  }

  // -- SEARCH --

  @Operation(summary = "Search platform users")
  @AccessControl(
      actionPerformed = Action.SEARCH,
      resourceType = ResourceType.PLATFORM_USER,
      isEnterpriseEdition = true)
  @PostMapping("/search")
  public Page<UserOutput> search(
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    return userService.search(searchPaginationInput);
  }

  @Operation(summary = "Find platform users by IDs")
  @AccessControl(
      actionPerformed = Action.READ,
      resourceType = ResourceType.PLATFORM_USER,
      isEnterpriseEdition = true)
  @PostMapping("/find")
  public List<UserOutput> find(@RequestBody @Valid @NotNull final List<String> userIds) {
    return userService.find(userIds).stream().map(UserMapper::toOutput).toList();
  }

  // -- UPDATE --

  @Operation(summary = "Update a platform user")
  @AccessControl(
      resourceId = "#userId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.PLATFORM_USER,
      isEnterpriseEdition = true)
  @PutMapping("/{userId}")
  public UserOutput update(@PathVariable String userId, @Valid @RequestBody UserInput input) {
    return toOutput(userService.updateUser(userId, input));
  }

  // -- DELETE --

  @Operation(summary = "Delete a platform user")
  @AccessControl(
      resourceId = "#userId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.PLATFORM_USER,
      isEnterpriseEdition = true)
  @DeleteMapping("/{userId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String userId) {
    userService.delete(userId);
  }
}
