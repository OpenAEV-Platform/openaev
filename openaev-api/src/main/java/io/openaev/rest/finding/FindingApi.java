package io.openaev.rest.finding;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.database.model.Action;
import io.openaev.database.model.Finding;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.finding.form.FindingArchiveSettingsInput;
import io.openaev.rest.finding.form.FindingArchiveSettingsOutput;
import io.openaev.rest.finding.form.FindingInput;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.service.settings.TenantSettingsService;
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
  private final TenantSettingsService tenantSettingsService;

  // -- CRUD --

  @GetMapping({FINDING_URI + "/{id}", TENANT_FINDING_URI + "/{id}"})
  @Transactional
  @AccessControl(
      resourceId = "#id",
      actionPerformed = Action.READ,
      resourceType = ResourceType.FINDING)
  public ResponseEntity<Finding> finding(@PathVariable @NotNull final String id) {
    return ResponseEntity.ok(this.findingService.finding(id));
  }

  @PostMapping({FINDING_URI, TENANT_FINDING_URI})
  @Transactional
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.FINDING)
  public ResponseEntity<Finding> createFinding(
      @RequestBody @Valid @NotNull final FindingInput input) {
    return ResponseEntity.ok(
        this.findingService.createFinding(input.toFinding(new Finding()), input.getInjectId()));
  }

  @PutMapping({FINDING_URI + "/{id}", TENANT_FINDING_URI + "/{id}"})
  @Transactional
  @AccessControl(
      resourceId = "#id",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.FINDING)
  public ResponseEntity<Finding> updateFinding(
      @PathVariable @NotNull final String id,
      @RequestBody @Valid @NotNull final FindingInput input) {
    Finding existingFinding = this.findingService.finding(id);
    Finding updatedFinding = input.toFinding(existingFinding);
    return ResponseEntity.ok(
        this.findingService.updateFinding(updatedFinding, input.getInjectId()));
  }

  @DeleteMapping({FINDING_URI + "/{id}", TENANT_FINDING_URI + "/{id}"})
  @Transactional
  @AccessControl(
      resourceId = "#id",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.FINDING)
  public ResponseEntity<Void> deleteFinding(@PathVariable @NotNull final String id) {
    this.findingService.deleteFinding(id);
    return ResponseEntity.noContent().build();
  }

  // -- ARCHIVE SETTINGS --
  // A finding is considered "archived" (frontend-computed, no persisted status) once it hasn't
  // been re-detected for more than this many days. Configurable per-tenant from the Finding page.

  @GetMapping(TENANT_FINDING_URI + "/settings/archive-days")
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.TENANT_SETTING)
  @Transactional(readOnly = true)
  public FindingArchiveSettingsOutput findArchiveDays(@PathVariable String tenantId) {
    return new FindingArchiveSettingsOutput(
        this.tenantSettingsService.findFindingArchiveDays(tenantId));
  }

  @PutMapping(TENANT_FINDING_URI + "/settings/archive-days")
  @Transactional
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.TENANT_SETTING)
  public FindingArchiveSettingsOutput updateArchiveDays(
      @PathVariable String tenantId,
      @RequestBody @Valid @NotNull final FindingArchiveSettingsInput input) {
    this.tenantSettingsService.updateFindingArchiveDays(tenantId, input.getArchiveDays());
    return new FindingArchiveSettingsOutput(
        this.tenantSettingsService.findFindingArchiveDays(tenantId));
  }
}
