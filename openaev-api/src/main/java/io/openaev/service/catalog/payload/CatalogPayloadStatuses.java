package io.openaev.service.catalog.payload;

import io.openaev.database.model.Payload;
import io.openaev.database.model.catalog.CatalogItemStatus;
import io.openaev.database.model.catalog.CatalogPayload;
import io.openaev.service.catalog.CatalogListService;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** What the catalogue of the tenant says of each payload version it lists. */
@Component
@RequiredArgsConstructor
public class CatalogPayloadStatuses {

  private final CatalogListService catalogListService;

  @Transactional(readOnly = true)
  public Snapshot current() {
    return new Snapshot(
        catalogListService.all(CatalogPayload.class).stream()
            .collect(
                Collectors.toMap(
                    item ->
                        new Version(
                            item.getRelease().getRepository(),
                            item.getExternalId(),
                            item.getVersion()),
                    CatalogPayload::getStatus)));
  }

  public record Version(String repository, String externalId, String version) {

    static Version of(Payload payload) {
      return new Version(
          payload.getCatalogRepository(),
          payload.getCatalogExternalId(),
          payload.getCatalogVersion());
    }

    public boolean is(Payload payload) {
      return equals(of(payload));
    }
  }

  public record Snapshot(Map<Version, CatalogItemStatus> statuses) {

    public List<Version> revoked() {
      return statuses.entrySet().stream()
          .filter(entry -> entry.getValue() == CatalogItemStatus.REVOKED)
          .map(Map.Entry::getKey)
          .toList();
    }

    public boolean revokes(Payload payload) {
      return statuses.get(Version.of(payload)) == CatalogItemStatus.REVOKED;
    }

    // A version the catalogue no longer lists stays revoked.
    public boolean clears(Payload payload) {
      CatalogItemStatus status = statuses.get(Version.of(payload));
      return status != null && status != CatalogItemStatus.REVOKED;
    }
  }
}
