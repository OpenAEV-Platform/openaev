package io.openaev.rest.finding;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.Finding;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.finding.form.FindingInput;
import io.openaev.rest.finding.form.FindingSummaryOutput;
import io.openaev.rest.helper.RestBehavior;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class FindingApi extends RestBehavior {

  public static final String FINDING_URI = "/api/findings";
  public static final String TENANT_FINDING_URI = TENANT_PREFIX + "/findings";

  private final FindingService findingService;

  // -- CRUD --

  /**
   * Resolves, inside the scoped transaction, every lazy association that {@code Finding}'s
   * serialization reaches.
   *
   * <p>Two of them reach v2-active tables. {@code Finding.getAssetGroups()} is a computed
   * {@code @JsonProperty} walking {@code inject.getAssetGroups()} on {@code asset_groups}, and
   * {@code Finding.assets} is a lazy {@code @ManyToMany} on {@code assets}. Jackson resolves both
   * AFTER this method returns, through open-in-view: the session is still open, so nothing throws,
   * but the transaction and its {@code app.current_tenants} scope are gone and the statement
   * inspector fail-closes the query. The endpoint then returns 200 with {@code
   * finding_asset_groups: []} and {@code finding_assets: []} for every finding, whatever the data.
   *
   * <p>{@code finding_tags}, {@code finding_teams} and {@code finding_users} are lazy too but their
   * tables are not active; they belong here the day they are.
   *
   * <p>Carrying a {@code TxCtx} is necessary and not sufficient; the load has to happen here. Same
   * fix and same reason as {@code SecurityPlatformApi.withManagerLinksInitialized} (#7026). {@code
   * FindingAssetGroupSinkTest} pins it, and pins it from a non-transactional test, because a
   * transactional one keeps the scope alive through serialization and proves nothing.
   */
  private static Finding withScopedAssociationsInitialized(Finding finding) {
    if (finding.getInject() != null) {
      Hibernate.initialize(finding.getInject().getAssetGroups());
      // One level deeper, and this is not decoration. Each asset group serializes its own assets as
      // ids through MultiIdListSerializer, which walks the lazy collection while Jackson runs -
      // after the transaction and its scope are gone. Initialising the groups alone leaves every
      // asset_group_assets array empty, which is the same defect as the one above, one level down.
      finding
          .getInject()
          .getAssetGroups()
          .forEach(group -> Hibernate.initialize(group.getAssets()));
    }
    Hibernate.initialize(finding.getAssets());
    return finding;
  }

  @GetMapping({FINDING_URI + "/{id}", TENANT_FINDING_URI + "/{id}"})
  @Transactional
  @AccessControl(
      resourceId = "#id",
      actionPerformed = Action.READ,
      resourceType = ResourceType.FINDING)
  public ResponseEntity<Finding> finding(TxCtx ctx, @PathVariable @NotNull final String id) {
    return ResponseEntity.ok(withScopedAssociationsInitialized(this.findingService.finding(id)));
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
  public ResponseEntity<Finding> createFinding(
      TxCtx ctx, @RequestBody @Valid @NotNull final FindingInput input) {
    return ResponseEntity.ok(
        withScopedAssociationsInitialized(
            this.findingService.createFinding(
                ctx, input.toFinding(new Finding()), input.getInjectId())));
  }

  @PutMapping({FINDING_URI + "/{id}", TENANT_FINDING_URI + "/{id}"})
  @Transactional
  @AccessControl(
      resourceId = "#id",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.FINDING)
  public ResponseEntity<Finding> updateFinding(
      TxCtx ctx,
      @PathVariable @NotNull final String id,
      @RequestBody @Valid @NotNull final FindingInput input) {
    Finding existingFinding = this.findingService.finding(id);
    Finding updatedFinding = input.toFinding(existingFinding);
    return ResponseEntity.ok(
        withScopedAssociationsInitialized(
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
