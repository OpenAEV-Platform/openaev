package io.openaev.service.catalog.payload;

import io.openaev.database.model.Payload;
import io.openaev.database.model.catalog.CatalogItemStatus;
import io.openaev.database.model.catalog.CatalogPayload;
import io.openaev.service.catalog.CatalogListService;
import io.openaev.service.catalog.CatalogRevocations;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    List<CatalogPayload> items = catalogListService.all(CatalogPayload.class);
    return new Snapshot(
        items.stream()
            .collect(
                Collectors.toMap(
                    item ->
                        new Version(
                            item.getRelease().getRepository(),
                            item.getExternalId(),
                            item.getVersion()),
                    CatalogPayload::getStatus)),
        CatalogRevocations.of(items));
  }

  public record Version(String repository, String externalId, String version) {

    static Version of(Payload payload) {
      return new Version(
          payload.getCatalogRepository(),
          payload.getCatalogExternalId(),
          payload.getCatalogVersion());
    }
  }

  public record Snapshot(Map<Version, CatalogItemStatus> statuses, CatalogRevocations revocations) {

    public Set<String> revokedExternalIds() {
      return revocations.externalIds();
    }

    public boolean revokes(Payload payload) {
      return revocations.revokes(payload.getCatalogExternalId(), payload.getCatalogVersion());
    }

    // A version the catalogue no longer lists stays revoked.
    public boolean clears(Payload payload) {
      CatalogItemStatus status = statuses.get(Version.of(payload));
      return status != null && !revokes(payload);
    }
  }
}
