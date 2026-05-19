package io.openaev.rest.finding;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.database.model.Action;
import io.openaev.database.model.Finding;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.finding.form.FindingInput;
import io.openaev.rest.helper.RestBehavior;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class FindingApi extends RestBehavior {

  public static final String FINDING_URI = "/api/findings";
  public static final String TENANT_FINDING_URI = TENANT_PREFIX + "/findings";

  private final FindingService findingService;

  // -- CRUD --

  @GetMapping({FINDING_URI + "/{id}", TENANT_FINDING_URI + "/{id}"})
  @AccessControl(
      resourceId = "#id",
      actionPerformed = Action.READ,
      resourceType = ResourceType.FINDING)
  public ResponseEntity<Finding> finding(@PathVariable @NotNull final String id) {
    return ResponseEntity.ok(this.findingService.finding(id));
  }

  @PostMapping({FINDING_URI, TENANT_FINDING_URI})
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.FINDING)
  public ResponseEntity<Finding> createFinding(
      @RequestBody @Valid @NotNull final FindingInput input) {
    return ResponseEntity.ok(
        this.findingService.createFinding(input.toFinding(new Finding()), input.getInjectId()));
  }

  @PutMapping({FINDING_URI + "/{id}", TENANT_FINDING_URI + "/{id}"})
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
  @AccessControl(
      resourceId = "#id",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.FINDING)
  public ResponseEntity<Void> deleteFinding(@PathVariable @NotNull final String id) {
    this.findingService.deleteFinding(id);
    return ResponseEntity.noContent().build();
  }
}
