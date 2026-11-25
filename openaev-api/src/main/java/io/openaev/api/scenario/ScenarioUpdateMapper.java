package io.openaev.api.scenario;

import static java.util.stream.Collectors.joining;

import io.openaev.api.scenario.dto.ScenarioCatalogStateOutput;
import io.openaev.api.scenario.dto.ScenarioChangeOutput;
import io.openaev.api.scenario.dto.ScenarioChangelogOutput;
import io.openaev.api.scenario.dto.ScenarioPayloadVersionOutput;
import io.openaev.api.scenario.dto.ScenarioRevisionOutput;
import io.openaev.api.scenario.dto.ScenarioUpdatePreviewOutput;
import io.openaev.api.scenario.dto.ScenarioVersionOutput;
import io.openaev.api.scenario.dto.ScenarioVersionsOutput;
import io.openaev.database.model.catalog.CatalogScenario;
import io.openaev.database.repository.ScenarioRevisionRepository.RevisionRow;
import io.openaev.service.catalog.scenario.update.NumberedRevision;
import io.openaev.service.catalog.scenario.update.ScenarioCatalogState;
import io.openaev.service.catalog.scenario.update.ScenarioMerge;
import io.openaev.service.catalog.scenario.update.ScenarioPayloadCheck;
import io.openaev.service.catalog.scenario.update.ScenarioPreview;
import io.openaev.service.catalog.scenario.update.ScenarioVersions;
import java.util.Objects;
import java.util.stream.Stream;

public class ScenarioUpdateMapper {

  private ScenarioUpdateMapper() {}

  public static ScenarioChangeOutput toOutput(ScenarioMerge.Change change) {
    return new ScenarioChangeOutput(
        change.id(),
        change.elementKind(),
        change.fieldKind(),
        change.fieldKey(),
        change.title(),
        change.status(),
        change.editable(),
        change.base(),
        change.applied(),
        change.local());
  }

  public static ScenarioUpdatePreviewOutput toOutput(ScenarioPreview preview) {
    return new ScenarioUpdatePreviewOutput(
        preview.merge().changes().stream().map(ScenarioUpdateMapper::toOutput).toList(),
        preview.payloads().toInstall().stream().map(ScenarioUpdateMapper::toOutput).toList(),
        preview.payloads().revoked().stream().map(ScenarioUpdateMapper::toOutput).toList(),
        preview.merge().lostDependencies(),
        preview.notes().stream().map(ScenarioUpdateMapper::toChangelog).toList());
  }

  private static ScenarioChangelogOutput toChangelog(CatalogScenario version) {
    return new ScenarioChangelogOutput(version.getVersion(), version.getChangelog());
  }

  private static ScenarioPayloadVersionOutput toOutput(ScenarioPayloadCheck.Version version) {
    return new ScenarioPayloadVersionOutput(version.name(), version.version());
  }

  public static ScenarioVersionsOutput toOutput(ScenarioVersions versions) {
    return new ScenarioVersionsOutput(
        versions.versions().stream().map(version -> toOutput(version, versions)).toList(),
        versions.simulationsCount());
  }

  private static ScenarioVersionOutput toOutput(CatalogScenario version, ScenarioVersions all) {
    return new ScenarioVersionOutput(
        version.getVersion(),
        version.getStatus(),
        version.getChangelog(),
        version.getPublishedAt(),
        version.getVersion().equals(all.installedVersion()),
        version.getVersion().equals(all.latestVersion()));
  }

  public static ScenarioCatalogStateOutput toOutput(ScenarioCatalogState state) {
    return new ScenarioCatalogStateOutput(
        state.revoked(), state.lastUpdate() == null ? null : toOutput(state.lastUpdate()));
  }

  public static ScenarioRevisionOutput toOutput(NumberedRevision numbered) {
    RevisionRow revision = numbered.revision();
    return new ScenarioRevisionOutput(
        revision.getId(),
        numbered.number(),
        revision.getReason(),
        revision.getCatalogVersion(),
        userName(revision),
        revision.getCreatedAt());
  }

  private static String userName(RevisionRow revision) {
    String name =
        Stream.of(revision.getUserFirstname(), revision.getUserLastname())
            .filter(Objects::nonNull)
            .collect(joining(" "));
    return name.isEmpty() ? null : name;
  }
}
