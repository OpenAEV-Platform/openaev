package io.openaev.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Catalogue sync guards")
class CatalogSyncGuardsTest {

  private static byte[] zip(String name, byte[] content) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(out)) {
      zip.putNextEntry(new ZipEntry(name));
      zip.write(content);
      zip.closeEntry();
    }
    return out.toByteArray();
  }

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
      assertThat(CatalogReleaseOrder.ORDER.compare("2026.10.30.10", "2026.10.30.9")).isPositive();
      assertThat(CatalogReleaseOrder.ORDER.compare("2026.9.1.1", "2026.10.1.1")).isNegative();
      assertThat(CatalogReleaseOrder.ORDER.compare("2026.10.30.1", "2026.10.30.1")).isZero();
    }

    @Test
    @DisplayName("Only YYYY.MM.DD.N names are releases")
    void given_otherName_should_beInvalid() {
      // Act & Assert
      assertThat(CatalogReleaseOrder.isValid("2026.10.30.123456789")).isTrue();
      assertThat(CatalogReleaseOrder.isValid("r1")).isFalse();
      assertThat(CatalogReleaseOrder.isValid("2026.10.30")).isFalse();
      assertThat(CatalogReleaseOrder.isValid(null)).isFalse();
    }
  }

  @Nested
  @DisplayName("Archives")
  class Archives {

    @Test
    @DisplayName("A plain archive is safe")
    void given_plainArchive_should_beSafe() throws Exception {
      // Arrange
      byte[] archive = zip("scenario.json", "{\"name\":\"EASM\"}".getBytes(StandardCharsets.UTF_8));

      // Act & Assert
      assertThat(CatalogArchiveChecks.unsafeReason(archive)).isEmpty();
    }

    @Test
    @DisplayName("An archive holding a symbolic link is refused")
    void given_symbolicLink_should_beRefused() throws Exception {
      // Arrange
      byte[] archive = asSymbolicLink(zip("link", "/etc/passwd".getBytes(StandardCharsets.UTF_8)));

      // Act & Assert
      assertThat(CatalogArchiveChecks.unsafeReason(archive))
          .contains("Archive holds a symbolic link");
    }

    @Test
    @DisplayName("An entry that decompresses far beyond its size is refused")
    void given_zipBomb_should_beRefused() throws Exception {
      // Arrange
      byte[] archive = zip("bomb.json", new byte[5 * 1024 * 1024]);

      // Act & Assert
      assertThat(CatalogArchiveChecks.unsafeReason(archive))
          .contains("Archive entry compressed too much: bomb.json");
    }
  }
}
