package io.openaev.rest.finding;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.finding.form.FindingArchiveBulkInput;
import io.openaev.rest.finding.form.FindingArchiveBulkItemOutput;
import io.openaev.rest.helper.RestBehavior;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class FindingArchiveApi extends RestBehavior {

  public static final String FINDING_ARCHIVE_BULK_URI = "/api/findings/archive/bulk";
  public static final String TENANT_FINDING_ARCHIVE_BULK_URI =
      TENANT_PREFIX + "/findings/archive/bulk";

  private final FindingArchiveService findingArchiveService;

  // No single resourceId to resolve (the target findings are in the request body, as a list) -
  // mirrors FindingTriageApi#triageFindingsBulk's capability-only (no resourceId) @AccessControl
  // usage.
  @PatchMapping({FINDING_ARCHIVE_BULK_URI, TENANT_FINDING_ARCHIVE_BULK_URI})
  @Transactional
  @AccessControl(actionPerformed = Action.ARCHIVE, resourceType = ResourceType.FINDING)
  public ResponseEntity<List<FindingArchiveBulkItemOutput>> archiveFindingsBulk(
      @RequestBody @Valid @NotNull final FindingArchiveBulkInput input) {
    return ResponseEntity.ok(
        findingArchiveService.archiveBulk(input.getFindingIds(), input.getArchived()));
  }
}
