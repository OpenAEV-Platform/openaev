package io.openaev.api.marking_definition;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.api.marking_definition.form.MarkingDefinitionInput;
import io.openaev.api.marking_definition.form.MarkingDefinitionOutput;
import io.openaev.config.TenantWriteScopeResolver;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.MarkingDefinition;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.service.marking_definition.MarkingDefinitionService;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping({MarkingDefinitionApi.TENANT_MARKING_DEFINITIONS_URI})
@Tag(name = "Marking definition API", description = "Operations related to marking definitions")
public class MarkingDefinitionApi extends RestBehavior {

  public static final String TENANT_MARKING_DEFINITIONS_URI =
      TENANT_PREFIX + "/marking_definitions";

  private final MarkingDefinitionService service;
  private final TenantWriteScopeResolver writeScopeResolver;

  // -- SEARCH --

  @LogExecutionTime
  @GetMapping
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.MARKING_DEFINITION)
  @Operation(summary = "Get marking definitions", description = "Get the list of marking definitions")
  public List<MarkingDefinitionOutput> list(TxCtx ctx) {
    return service.list(ctx).stream().map(MarkingDefinitionMapper::toOutput).toList();
  }

  @LogExecutionTime
  @PostMapping("/search")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.MARKING_DEFINITION)
  @Operation(summary = "Search marking definitions")
  public Page<MarkingDefinitionOutput> search(
      TxCtx ctx, @RequestBody @Valid SearchPaginationInput searchPaginationInput) {
    return service.search(ctx, searchPaginationInput).map(MarkingDefinitionMapper::toOutput);
  }

  // -- CREATE --

  @PostMapping
  @Transactional
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.MARKING_DEFINITION)
  @Operation(summary = "Create a marking definition")
  public MarkingDefinitionOutput create(
      TxCtx ctx, @Valid @RequestBody MarkingDefinitionInput input) {
    String tenantId = writeScopeResolver.tenantForWrite(ctx, null);
    MarkingDefinition created = service.create(input, tenantId);
    return MarkingDefinitionMapper.toOutput(created);
  }

  // -- UPDATE --

  @PutMapping("/{markingDefinitionId}")
  @Transactional
  @AccessControl(
      resourceId = "#markingDefinitionId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.MARKING_DEFINITION)
  @Operation(summary = "Update a marking definition")
  public MarkingDefinitionOutput update(
      TxCtx ctx,
      @PathVariable String markingDefinitionId,
      @Valid @RequestBody MarkingDefinitionInput input) {
    return MarkingDefinitionMapper.toOutput(service.update(ctx, markingDefinitionId, input));
  }

  // -- DELETE --

  @DeleteMapping("/{markingDefinitionId}")
  @Transactional
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @AccessControl(
      resourceId = "#markingDefinitionId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.MARKING_DEFINITION)
  @Operation(summary = "Delete a marking definition")
  public void delete(TxCtx ctx, @PathVariable String markingDefinitionId) {
    service.delete(ctx, markingDefinitionId);
  }
}
