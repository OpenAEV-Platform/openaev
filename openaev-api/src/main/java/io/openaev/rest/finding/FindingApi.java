package io.openaev.rest.finding;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.Finding;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.finding.form.FindingInput;
import io.openaev.rest.finding.form.FindingOutput;
import io.openaev.rest.finding.form.FindingSummaryOutput;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.utils.mapper.FindingMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class FindingApi extends RestBehavior {

  public static final String FINDING_URI = "/api/findings";
  public static final String TENANT_FINDING_URI = TENANT_PREFIX + "/findings";

  private final FindingService findingService;
  private final FindingMapper findingMapper;

  // -- CRUD --

  /*
   * On the lazy associations of Finding and the v2 tenant scope.
   *
   * main resolved them by hand before returning the entity (withScopedAssociationsInitialized,
   * #7856): Finding.getAssetGroups() and Finding.assets are lazy and reach v2-active tables, and
   * Jackson used to walk them AFTER the controller returned, through open-in-view - session still
   * open, but transaction and app.current_tenants scope gone, so the statement inspector
   * fail-closed the query and the endpoint answered 200 with empty arrays.
   *
   * These endpoints no longer serialize the entity: FindingMapper.toFindingOutput builds the DTO
   * inside the transaction, so every association is read while the scope is still alive. The
   * defect is therefore addressed by construction rather than by an explicit initialisation list -
   * which is also why the note main left on finding_tags, finding_teams and finding_users, "they
   * belong here the day they are active", no longer needs acting on: they are read in the same
   * place, active or not.
   *
   * FindingAssetGroupSinkTest still pins the observable behaviour, and still from a
   * non-transactional test.
   */

  @GetMapping({FINDING_URI + "/{id}", TENANT_FINDING_URI + "/{id}"})
  @Transactional(readOnly = true)
  @AccessControl(
      resourceId = "#id",
      actionPerformed = Action.READ,
      resourceType = ResourceType.FINDING)
  public ResponseEntity<FindingOutput> finding(TxCtx ctx, @PathVariable @NotNull final String id) {
    return ResponseEntity.ok(this.findingMapper.toFindingOutput(this.findingService.finding(id)));
  }

  @GetMapping({FINDING_URI + "/{id}/summary", TENANT_FINDING_URI + "/{id}/summary"})
  @Transactional
  @AccessControl(
      resourceId = "#id",
      actionPerformed = Action.READ,
      resourceType = ResourceType.FINDING)
  public ResponseEntity<FindingSummaryOutput> findingSummary(
      TxCtx ctx, @PathVariable @NotNull final String id) {
    return ResponseEntity.ok(this.findingService.findingSummary(id));
  }

  @PostMapping({FINDING_URI, TENANT_FINDING_URI})
  @Transactional
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.FINDING)
  public ResponseEntity<FindingOutput> createFinding(
      TxCtx ctx, @RequestBody @Valid @NotNull final FindingInput input) {
    return ResponseEntity.ok(
        this.findingMapper.toFindingOutput(
            this.findingService.createFinding(
                ctx, input.toFinding(new Finding()), input.getInjectId())));
  }

  @PutMapping({FINDING_URI + "/{id}", TENANT_FINDING_URI + "/{id}"})
  @Transactional
  @AccessControl(
      resourceId = "#id",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.FINDING)
  public ResponseEntity<FindingOutput> updateFinding(
      TxCtx ctx,
      @PathVariable @NotNull final String id,
      @RequestBody @Valid @NotNull final FindingInput input) {
    Finding existingFinding = this.findingService.finding(id);
    Finding updatedFinding = input.toFinding(existingFinding);
    return ResponseEntity.ok(
        this.findingMapper.toFindingOutput(
            this.findingService.updateFinding(updatedFinding, input.getInjectId())));
  }

  @DeleteMapping({FINDING_URI + "/{id}", TENANT_FINDING_URI + "/{id}"})
  @Transactional
  @AccessControl(
      resourceId = "#id",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.FINDING)
  public ResponseEntity<Void> deleteFinding(TxCtx ctx, @PathVariable @NotNull final String id) {
    this.findingService.deleteFinding(id);
    return ResponseEntity.noContent().build();
  }
}
