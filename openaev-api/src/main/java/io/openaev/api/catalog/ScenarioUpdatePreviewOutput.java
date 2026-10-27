package io.openaev.api.catalog;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.service.scenario.revision.ScenarioApplyService.Preview;
import io.openaev.service.scenario.revision.ScenarioMerge;
import java.util.List;

public record ScenarioUpdatePreviewOutput(
    @JsonProperty("scenario_id") String scenarioId,
    @JsonProperty("current_version") String currentVersion,
    @JsonProperty("target_version") String targetVersion,
    @JsonProperty("target_revision_id") String targetRevisionId,
    @JsonProperty("last_revision") int lastRevision,
    @JsonProperty("local_hash") String localHash,
    @JsonProperty("changes") List<Change> changes,
    @JsonProperty("elements") List<Element> elements,
    @JsonProperty("warnings") List<String> warnings,
    @JsonProperty("conflicts") List<String> conflicts,
    @JsonProperty("payloads_to_install") List<String> payloadsToInstall,
    @JsonProperty("revoked_payloads") List<String> revokedPayloads) {

  public record Change(
      @JsonProperty("id") String id,
      @JsonProperty("element") String element,
      @JsonProperty("title") String title,
      @JsonProperty("block") String block,
      @JsonProperty("key") String key,
      @JsonProperty("status") ScenarioMerge.Status status,
      @JsonProperty("editable") boolean editable,
      @JsonProperty("base") JsonNode base,
      @JsonProperty("applied") JsonNode applied,
      @JsonProperty("local") JsonNode local) {}

  public record Element(
      @JsonProperty("id") String id,
      @JsonProperty("kind") String kind,
      @JsonProperty("title") String title,
      @JsonProperty("change") ScenarioMerge.ElementChange change) {}

  public static ScenarioUpdatePreviewOutput of(Preview preview) {
    ScenarioMerge.Result merge = preview.merge();
    return new ScenarioUpdatePreviewOutput(
        preview.scenario().getId(),
        preview.scenario().getVersion(),
        preview.targetVersion(),
        preview.target().revisionId(),
        preview.lastRevision(),
        preview.localHash(),
        merge.changes().stream()
            .map(
                c ->
                    new Change(
                        c.id(),
                        c.element(),
                        c.title(),
                        c.block(),
                        c.key(),
                        c.status(),
                        ScenarioMerge.editable(c.block(), c.key()),
                        c.base(),
                        c.applied(),
                        c.local()))
            .toList(),
        merge.elements().stream()
            .map(e -> new Element(e.id(), e.kind(), e.title(), e.change()))
            .toList(),
        merge.warnings(),
        List.copyOf(merge.unresolved()),
        preview.payloadsToInstall(),
        preview.revokedPayloads());
  }
}
