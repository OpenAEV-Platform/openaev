package io.openaev.api.marking_definition;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import io.openaev.service.UserService;
import io.openaev.service.marking_definition.MarkingDefinitionService;
import io.openaev.service.marking_definition.MarkingDefinitionStixService;
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
  private final MarkingDefinitionStixService stixService;
  private final TenantWriteScopeResolver writeScopeResolver;
  private final UserService userService;

  // -- SEARCH --

  @LogExecutionTime
  @GetMapping("/assignable")
  @Transactional(readOnly = true)
  // No capability needed: the result is already narrowed to the caller's own clearance.
  @AccessControl(skipRBAC = true)
  @Operation(
      summary = "Get the marking definitions the current user is cleared for",
      description =
          "The marking definitions of the tenants in scope narrowed to the caller's own clearance"
              + " (cumulative per type - holding TLP:AMBER also covers TLP:GREEN and TLP:CLEAR), the"
              + " same rule enforced server-side when a marking is actually assigned. Built for an"
              + " assignment picker and for the marking filter's value picker.")
  public List<MarkingDefinitionOutput> assignable(TxCtx ctx) {
    return service.listAssignable(ctx).stream().map(MarkingDefinitionMapper::toOutput).toList();
  }

  @LogExecutionTime
  @GetMapping("/{markingDefinitionId}/assigned")
  @Transactional(readOnly = true)
  @AccessControl(
      resourceId = "#markingDefinitionId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.MARKING_DEFINITION)
  @Operation(summary = "Check if a marking definition is currently assigned to any entity")
  public boolean assigned(TxCtx ctx, @PathVariable String markingDefinitionId) {
    return service.isAssigned(ctx, markingDefinitionId);
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

  // -- STIX (OpenCTI format) --

  @LogExecutionTime
  @GetMapping("/stix")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.MARKING_DEFINITION)
  @Operation(
      summary = "Export marking definitions in OpenCTI's STIX 2.1 format",
      description =
          "A STIX 2.1 bundle of marking-definition objects, each with the STIX id OpenCTI gives"
              + " the marking and OpenCTI's order and color.")
  public ObjectNode exportStix(TxCtx ctx) {
    return stixService.exportBundle(ctx);
  }

  @PostMapping("/stix")
  @Transactional
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.MARKING_DEFINITION)
  @Operation(
      summary = "Import marking definitions from OpenCTI's STIX 2.1 format",
      description =
          "Creates the marking-definition objects of a STIX bundle the tenant does not have,"
              + " keeping their STIX id. Nothing is overwritten: each object is reported CREATED,"
              + " EXISTING (same STIX id), CONFLICT (same type and definition, another id) or"
              + " INVALID.")
  public List<MarkingDefinitionStixService.ImportLine> importStix(
      TxCtx ctx, @RequestBody JsonNode stix) {
    String tenantId = writeScopeResolver.tenantForWrite(ctx, null);
    return stixService.importStix(stix, tenantId, userService.currentUser().getId());
  }

  // -- CREATE --

  @PostMapping
  @Transactional
  @ResponseStatus(HttpStatus.CREATED)
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.MARKING_DEFINITION)
  @Operation(summary = "Create a marking definition")
  public MarkingDefinitionOutput create(
      TxCtx ctx, @Valid @RequestBody MarkingDefinitionInput input) {
    String tenantId = writeScopeResolver.tenantForWrite(ctx, null);
    MarkingDefinition created = service.create(input, tenantId, userService.currentUser().getId());
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
    return MarkingDefinitionMapper.toOutput(
        service.update(ctx, markingDefinitionId, input, userService.currentUser().getId()));
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
    service.delete(ctx, markingDefinitionId, userService.currentUser().getId());
  }
}
