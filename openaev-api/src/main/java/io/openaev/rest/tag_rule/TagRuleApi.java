package io.openaev.rest.tag_rule;

import io.openaev.aop.LogExecutionTime;
import io.openaev.aop.RBAC;
import io.openaev.aop.UserRoleDescription;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.tag_rule.form.TagRuleInput;
import io.openaev.rest.tag_rule.form.TagRuleMapper;
import io.openaev.rest.tag_rule.form.TagRuleOutput;
import io.openaev.service.TagRuleService;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@UserRoleDescription
@Tag(
    name = "Tag rules management",
    description =
        "Endpoints to manage TagRules. TagRules are used to automatically add tags to elements depending on rules")
public class TagRuleApi extends RestBehavior {

  public static final String TAG_RULE_URI = "/api/tag-rules";

  private final TagRuleService tagRuleService;
  private final TagRuleMapper tagRuleMapper;

  public TagRuleApi(TagRuleService tagRuleService, TagRuleMapper tagRuleMapper) {
    super();
    this.tagRuleService = tagRuleService;
    this.tagRuleMapper = tagRuleMapper;
  }

  @LogExecutionTime
  @GetMapping(TagRuleApi.TAG_RULE_URI + "/{tagRuleId}")
  @RBAC(
      resourceId = "#tagRuleId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.TAG_RULE)
  @Operation(description = "Get TagRule by Id", summary = "Get TagRule")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The TagRule")})
  public TagRuleOutput findTagRule(
      @PathVariable @NotBlank @Schema(description = "ID of the tag rule") final String tagRuleId) {
    return tagRuleService.findById(tagRuleId).map(tagRuleMapper::toTagRuleOutput).orElse(null);
  }

  @LogExecutionTime
  @GetMapping(TagRuleApi.TAG_RULE_URI)
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.TAG_RULE)
  @Operation(description = "Get All TagRules", summary = "Get TagRules")
  @ApiResponses(
      value = {@ApiResponse(responseCode = "200", description = "The list of all TagRules")})
  public List<TagRuleOutput> tags() {
    return tagRuleService.findAll().stream().map(tagRuleMapper::toTagRuleOutput).toList();
  }

  @LogExecutionTime
  @DeleteMapping(TagRuleApi.TAG_RULE_URI + "/{tagRuleId}")
  @RBAC(
      resourceId = "#tagRuleId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.TAG_RULE)
  @Transactional(rollbackFor = Exception.class)
  @Operation(summary = "Delete TagRule", description = "TagRule needs to exists")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "TagRule deleted"),
        @ApiResponse(responseCode = "404", description = "TagRule not found")
      })
  public void deleteTagRule(
      @PathVariable @NotBlank @Schema(description = "ID of the tag rule") final String tagRuleId) {
    tagRuleService.deleteTagRule(tagRuleId);
  }

  @LogExecutionTime
  @PostMapping(TagRuleApi.TAG_RULE_URI)
  @RBAC(actionPerformed = Action.CREATE, resourceType = ResourceType.TAG_RULE)
  @Transactional(rollbackFor = Exception.class)
  @Operation(summary = "Create TagRule", description = "Tag and Asset Groups needs to exists")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "TagRule created"),
        @ApiResponse(responseCode = "404", description = "Tag or Asset Group not found")
      })
  public TagRuleOutput createTagRule(@Valid @RequestBody final TagRuleInput input) {
    return tagRuleMapper.toTagRuleOutput(
        tagRuleService.createTagRule(input.getTagName(), input.getAssetGroups()));
  }

  @LogExecutionTime
  @PutMapping(TagRuleApi.TAG_RULE_URI + "/{tagRuleId}")
  @RBAC(
      resourceId = "#tagRuleId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.TAG_RULE)
  @Transactional(rollbackFor = Exception.class)
  @Operation(summary = "Update TagRule", description = "Tag and Asset Groups needs to exists")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "TagRule updated"),
        @ApiResponse(responseCode = "404", description = "TagRule, Tag or Asset Group not found")
      })
  public TagRuleOutput updateTagRule(
      @PathVariable @NotBlank @Schema(description = "ID of the tag rule") final String tagRuleId,
      @Valid @RequestBody final TagRuleInput input) {
    return tagRuleMapper.toTagRuleOutput(
        tagRuleService.updateTagRule(tagRuleId, input.getTagName(), input.getAssetGroups()));
  }

  @LogExecutionTime
  @PostMapping(TagRuleApi.TAG_RULE_URI + "/search")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.TAG_RULE)
  @Operation(
      description = "Search TagRules corresponding to search criteria",
      summary = "Search TagRules")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "The list of all TagRules corresponding to the search criteria")
      })
  public Page<TagRuleOutput> searchTagRules(
      @RequestBody @Valid SearchPaginationInput searchPaginationInput) {
    return tagRuleService.searchTagRule(searchPaginationInput).map(tagRuleMapper::toTagRuleOutput);
  }
}
