package io.openaev.api.markings;

import static io.openaev.api.markings.MarkingDefinitionMapper.toOutput;
import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.api.markings.form.MarkingDefinitionInput;
import io.openaev.api.markings.response.MarkingDefinitionOutput;
import io.openaev.config.TenantWriteScopeResolver;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * The marking catalogue: the vocabulary that clearances and row attachments are both expressed in.
 *
 * <p>TODO: add Capa - Guarded by the existing tenant-settings capability chain rather than a marking-specific one —
 * step 2.1 of the marking design is deliberately capability-free.
 *
 * */
@RestController
@RequestMapping({MarkingDefinitionApi.MARKING_URI, MarkingDefinitionApi.TENANT_MARKING_URI})
@RequiredArgsConstructor
@Tag(name = "Marking definitions", description = "Manage the tenant's classification scales")
public class MarkingDefinitionApi extends RestBehavior {

  public static final String MARKING_URI = "/api/marking-definitions";

  public static final String TENANT_MARKING_URI = TENANT_PREFIX + "/marking-definitions";

  private final MarkingDefinitionService markingDefinitionService;
  private final TenantWriteScopeResolver writeScopeResolver;

  // -- CREATE --

  @Operation(summary = "Create a marking definition")
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.MARKING_DEFINITION)
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public MarkingDefinitionOutput create(
      TxCtx ctx, @Valid @RequestBody MarkingDefinitionInput input) {
    // Explicit write attribution: the row is stamped with the single tenant in the request scope,
    // and the inspector rejects the INSERT if that tenant is outside it.
    return toOutput(
        markingDefinitionService.create(writeScopeResolver.tenantForWrite(ctx, null), input));
  }

  // -- READ --

  @Operation(summary = "Get a marking definition by ID")
  @AccessControl(
      resourceId = "#markingId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.MARKING_DEFINITION)
  @GetMapping("/{markingId}")
  @Transactional(readOnly = true)
  public MarkingDefinitionOutput getById(TxCtx ctx, @PathVariable String markingId) {
    return toOutput(markingDefinitionService.findById(markingId));
  }

  // -- SEARCH --

  @Operation(summary = "Search marking definitions with pagination and filtering")
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.MARKING_DEFINITION)
  @PostMapping("/search")
  @Transactional(readOnly = true)
  public Page<MarkingDefinitionOutput> search(
      TxCtx ctx, @Valid @RequestBody SearchPaginationInput searchPaginationInput) {
    return markingDefinitionService.search(searchPaginationInput);
  }

  // -- UPDATE --

  @Operation(summary = "Update a marking definition")
  @AccessControl(
      resourceId = "#markingId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.MARKING_DEFINITION)
  @PutMapping("/{markingId}")
  @Transactional
  public MarkingDefinitionOutput update(
      TxCtx ctx, @PathVariable String markingId, @Valid @RequestBody MarkingDefinitionInput input) {
    return toOutput(markingDefinitionService.update(markingId, input));
  }

  // -- DELETE --

  @Operation(summary = "Delete a marking definition")
  @AccessControl(
      resourceId = "#markingId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.MARKING_DEFINITION)
  @DeleteMapping("/{markingId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Transactional
  public void delete(TxCtx ctx, @PathVariable String markingId) {
    markingDefinitionService.delete(markingId);
  }
}
