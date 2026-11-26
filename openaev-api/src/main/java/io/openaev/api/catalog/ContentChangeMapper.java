package io.openaev.api.catalog;

import io.openaev.api.catalog.dto.ContentChangeOutput;
import io.openaev.api.catalog.dto.PayloadContentChangeOutput;
import io.openaev.api.threat_arsenal.PayloadVersionChangeMapper;
import io.openaev.database.model.User;
import io.openaev.database.model.catalog.ContentChange;
import io.openaev.service.catalog.history.PayloadHistoryEntry;
import io.openaev.service.payload.PayloadVersionChange;
import java.util.Optional;

public class ContentChangeMapper {

  private ContentChangeMapper() {}

  public static ContentChangeOutput toOutput(ContentChange change) {
    return new ContentChangeOutput(
        change.getId(),
        change.getAction(),
        change.getMode(),
        change.getVersion(),
        Optional.ofNullable(change.getUser()).map(User::getNameOrEmail).orElse(null),
        change.getCreatedAt());
  }

  public static PayloadContentChangeOutput toPayloadOutput(PayloadHistoryEntry entry) {
    return new PayloadContentChangeOutput(
        toOutput(entry.change()),
        entry.versionChange().map(PayloadVersionChange::fromVersion).orElse(null),
        entry
            .versionChange()
            .map(PayloadVersionChange::change)
            .map(PayloadVersionChangeMapper::toOutputs)
            .orElse(null));
  }
}
