package io.openaev.rest.tag;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.aop.UserRoleDescription;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.Tag;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.tag.form.TagCreateInput;
import io.openaev.rest.tag.form.TagUpdateInput;
import io.openaev.utils.FilterUtilsJpa;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@io.swagger.v3.oas.annotations.tags.Tag(
    name = "Tags management",
    description = "Endpoints to manage tags")
@UserRoleDescription
public class TagApi extends RestBehavior {

  public static final String TAG_URI = "/api/tags";
  private static final String TENANT_TAG_URI = TENANT_PREFIX + "/tags";

  private final TagService tagService;

  // -- CREATE --

  @Operation(summary = "Create tag")
  @PostMapping({TAG_URI, TENANT_TAG_URI})
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.TAG)
  @Transactional(rollbackOn = Exception.class)
  public Tag createTag(@Valid @RequestBody TagCreateInput input) {
    return tagService.createTag(input);
  }

  @Operation(summary = "Upsert tag")
  @PostMapping({TAG_URI + "/upsert", TENANT_TAG_URI + "/upsert"})
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.TAG)
  @Transactional(rollbackOn = Exception.class)
  public Tag upsertTag(@Valid @RequestBody TagCreateInput input) {
    return tagService.upsertTag(input);
  }

  // -- READ --

  @Operation(summary = "Get tags", description = "Get the list of tags")
  @GetMapping({TAG_URI, TENANT_TAG_URI})
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.TAG)
  public Iterable<Tag> tags() {
    return tagService.tags();
  }

  @Operation(summary = "Search tags", description = "Search tags corresponding to the criteria")
  @PostMapping({TAG_URI + "/search", TENANT_TAG_URI + "/search"})
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.TAG)
  public Page<Tag> tags(@RequestBody @Valid SearchPaginationInput searchPaginationInput) {
    return tagService.search(searchPaginationInput);
  }

  // -- UPDATE --

  @Operation(summary = "Update tag")
  @PutMapping({TAG_URI + "/{tagId}", TENANT_TAG_URI + "/{tagId}"})
  @AccessControl(
      resourceId = "#tagId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.TAG)
  @Transactional(rollbackOn = Exception.class)
  public Tag updateTag(
      @PathVariable @Schema(description = "ID of the tag") String tagId,
      @Valid @RequestBody TagUpdateInput input) {
    return tagService.updateTag(tagId, input);
  }

  // -- DELETE --

  @Operation(summary = "Delete tag")
  @DeleteMapping({TAG_URI + "/{tagId}", TENANT_TAG_URI + "/{tagId}"})
  @AccessControl(
      resourceId = "#tagId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.TAG)
  public void deleteTag(@PathVariable @Schema(description = "ID of the tag") String tagId) {
    tagService.deleteTag(tagId);
  }

  // -- OPTIONS --

  @Operation(summary = "Search tags by text")
  @GetMapping({TAG_URI + "/options", TENANT_TAG_URI + "/options"})
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.TAG)
  public List<FilterUtilsJpa.Option> optionsByName(
      @RequestParam(required = false) @Schema(description = "Search text")
          final String searchText) {
    return tagService.optionsByName(searchText);
  }

  @Operation(summary = "Search tags by ids")
  @PostMapping({TAG_URI + "/options", TENANT_TAG_URI + "/options"})
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.TAG)
  public List<FilterUtilsJpa.Option> optionsById(@RequestBody final List<String> ids) {
    return tagService.optionsById(ids);
  }
}
