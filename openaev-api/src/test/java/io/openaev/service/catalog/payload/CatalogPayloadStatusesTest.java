package io.openaev.service.catalog.payload;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.database.model.Command;
import io.openaev.database.model.Payload;
import io.openaev.database.model.catalog.CatalogItemStatus;
import io.openaev.service.catalog.CatalogRevocations;
import io.openaev.service.catalog.payload.CatalogPayloadStatuses.Snapshot;
import io.openaev.service.catalog.payload.CatalogPayloadStatuses.Version;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("What the catalogue says of installed payload versions")
class CatalogPayloadStatusesTest {

  private static final String REPOSITORY = "filigran/openaev-payloads";

  private static final Version REVOKED = new Version(REPOSITORY, "dump", "1.0.0");
  private static final Version VERIFIED = new Version(REPOSITORY, "dump", "2.0.0");

  private final Snapshot snapshot =
      new Snapshot(
          Map.of(REVOKED, CatalogItemStatus.REVOKED, VERIFIED, CatalogItemStatus.VERIFIED),
          new CatalogRevocations(Map.of("dump", Set.of("1.0.0")), Set.of()));

  private static Payload installed(String version) {
    Payload payload = new Command();
    payload.getIdentity().setRepository(REPOSITORY);
    payload.setExternalId("dump");
    payload.setVersion(version);
    return payload;
  }

  @Test
  @DisplayName("A revoked version is revoked and never cleared")
  void given_revokedVersion_should_revokeIt() {
    // Act & Assert
    assertThat(snapshot.revokedExternalIds()).containsExactly("dump");
    assertThat(snapshot.revokes(installed("1.0.0"))).isTrue();
    assertThat(snapshot.clears(installed("1.0.0"))).isFalse();
  }

  @Test
  @DisplayName("A version listed again as offered is cleared")
  void given_verifiedVersion_should_clearIt() {
    // Act & Assert
    assertThat(snapshot.revokes(installed("2.0.0"))).isFalse();
    assertThat(snapshot.clears(installed("2.0.0"))).isTrue();
  }

  @Test
  @DisplayName("A version the catalogue no longer lists stays as it is")
  void given_unlistedVersion_should_neitherRevokeNorClear() {
    // Act & Assert
    assertThat(snapshot.revokes(installed("3.0.0"))).isFalse();
    assertThat(snapshot.clears(installed("3.0.0"))).isFalse();
  }
}
