package io.openaev.service.catalog;

import static io.openaev.utils.fixtures.CatalogReleaseFixture.archive;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.database.model.catalog.CatalogItem;
import io.openaev.database.model.catalog.CatalogItemStatus;
import io.openaev.database.model.catalog.CatalogScenario;
import io.openaev.utils.catalog.SemanticVersion;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("Catalogue sync guards")
class CatalogSyncGuardsTest {

  // Writes the Unix symbolic link mode into the external attributes of the first central entry.
  private static byte[] asSymbolicLink(byte[] archive) {
    ByteBuffer buffer = ByteBuffer.wrap(archive).order(ByteOrder.LITTLE_ENDIAN);
    for (int i = 0; i < archive.length - 4; i++) {
      if (buffer.getInt(i) == 0x02014b50) {
        buffer.putInt(i + 38, 0xA1FF << 16);
        break;
      }
    }
    return archive;
  }

  @Nested
  @DisplayName("Release order")
  class ReleaseOrder {

    @Test
    @DisplayName("Releases compare as numbers, not as text")
    void given_longerCounter_should_beNewer() {
      // Act & Assert
      assertThat(SemanticVersion.ORDER.compare("1.261104.10", "1.261104.9")).isPositive();
      assertThat(SemanticVersion.ORDER.compare("1.261103.0", "1.261029.10")).isPositive();
      assertThat(SemanticVersion.ORDER.compare("2.260101.0", "1.261231.5")).isPositive();
      assertThat(SemanticVersion.ORDER.compare("1.261104.0", "1.261104.0")).isZero();
    }

    @Test
    @DisplayName("A pre-release comes before its release, and compares identifier by identifier")
    void given_preRelease_should_comeBeforeRelease() {
      // Act & Assert
      assertThat(SemanticVersion.ORDER.compare("1.0.0-rc.1", "1.0.0")).isNegative();
      assertThat(SemanticVersion.ORDER.compare("1.0.0-rc.10", "1.0.0-rc.9")).isPositive();
      assertThat(SemanticVersion.ORDER.compare("1.0.0-1", "1.0.0-alpha")).isNegative();
      assertThat(SemanticVersion.ORDER.compare("1.0.0-alpha.1", "1.0.0-alpha")).isPositive();
      assertThat(SemanticVersion.ORDER.compare("v1.0.0+build.5", "1.0.0")).isZero();
    }

    @Test
    @DisplayName("Only semantic versions are releases")
    void given_otherName_should_beInvalid() {
      // Act & Assert
      assertThat(SemanticVersion.isValid("1.261104.0")).isTrue();
      assertThat(SemanticVersion.isValid("2026.10.30.1")).isFalse();
      assertThat(SemanticVersion.isValid("r1")).isFalse();
      assertThat(SemanticVersion.isValid(null)).isFalse();
    }
  }

  @Nested
  @DisplayName("Archives")
  class Archives {

    private static byte[] entries(int count) throws Exception {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      try (ZipOutputStream zip = new ZipOutputStream(out)) {
        for (int n = 0; n < count; n++) {
          zip.putNextEntry(new ZipEntry("entry-" + n + ".json"));
          zip.closeEntry();
        }
      }
      return out.toByteArray();
    }

    static Stream<Arguments> cases() throws Exception {
      byte[] json = "{}".getBytes(StandardCharsets.UTF_8);
      return Stream.of(
          Arguments.of("plain archive", archive("scenario.json", json), null),
          Arguments.of(
              "symbolic link",
              asSymbolicLink(archive("link", json)),
              "Archive holds a symbolic link"),
          Arguments.of(
              "zip bomb",
              archive("bomb.json", new byte[5 * 1024 * 1024]),
              "Archive entry compressed too much: bomb.json"),
          Arguments.of(
              "absolute path",
              archive("/etc/passwd", json),
              "Archive entry outside its folder: /etc/passwd"),
          Arguments.of(
              "too many entries",
              entries(CatalogArchiveChecks.MAX_ENTRIES + 1),
              "Archive has more than " + CatalogArchiveChecks.MAX_ENTRIES + " entries"),
          Arguments.of("empty archive", entries(0), "Empty archive"),
          Arguments.of("not a zip", json, "Unreadable archive"),
          Arguments.of(
              "too large",
              new byte[(int) CatalogArchiveChecks.MAX_ARCHIVE_BYTES + 1],
              "Archive larger than " + CatalogArchiveChecks.MAX_ARCHIVE_BYTES + " bytes"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    @DisplayName("An archive is safe, or refused with its reason")
    void given_archive_should_beSafeOrRefused(String label, byte[] archive, String reason) {
      // Act & Assert
      assertThat(CatalogArchiveChecks.unsafeReason(archive)).isEqualTo(Optional.ofNullable(reason));
    }
  }

  @Nested
  @DisplayName("Catalogue listing")
  class Listing {

    private CatalogItem item(CatalogItemStatus status) {
      CatalogItem item = new CatalogScenario();
      item.setStatus(status);
      return item;
    }

    @Test
    @DisplayName("Deprecated and revoked versions are not offered, any other is")
    void given_status_should_tellWhetherOffered() {
      // Act & Assert
      assertThat(CatalogListService.isOffered(item(CatalogItemStatus.VERIFIED))).isTrue();
      assertThat(CatalogListService.isOffered(item(CatalogItemStatus.DEPRECATED))).isFalse();
      assertThat(CatalogListService.isOffered(item(CatalogItemStatus.REVOKED))).isFalse();
    }

    @Test
    @DisplayName("A manifest status is read whatever its case, a missing one means verified")
    void given_manifestStatus_should_mapToEnum() {
      // Act & Assert
      assertThat(CatalogItemStatus.fromManifest(null)).isEqualTo(CatalogItemStatus.VERIFIED);
      assertThat(CatalogItemStatus.fromManifest("revoked")).isEqualTo(CatalogItemStatus.REVOKED);
      assertThatThrownBy(() -> CatalogItemStatus.fromManifest("BETA"))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }
}
