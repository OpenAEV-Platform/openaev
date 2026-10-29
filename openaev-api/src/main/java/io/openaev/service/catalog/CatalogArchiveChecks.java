package io.openaev.service.catalog;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Consumer checks of RFC 0004 §4.6 run before a catalogue archive is used: integrity and size. */
public final class CatalogArchiveChecks {

  // RFC 0004 question 1 positions: archive 20 MiB, decompressed 100 MiB, 1,000 entries.
  static final long MAX_ARCHIVE_BYTES = 20L * 1024 * 1024;
  static final long MAX_DECOMPRESSED_BYTES = 100L * 1024 * 1024;
  static final int MAX_ENTRIES = 1000;

  private CatalogArchiveChecks() {}

  // A release sends a payload version as a plain JSON file, not a zip.
  public static boolean isVersionFile(byte[] archive) {
    for (byte b : archive) {
      if (!Character.isWhitespace(b)) {
        return b == '{';
      }
    }
    return false;
  }

  public static String sha256(byte[] data) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Why the archive is refused, or empty when it is safe to open. */
  public static Optional<String> unsafeReason(byte[] archive) {
    if (archive.length > MAX_ARCHIVE_BYTES) {
      return Optional.of("Archive larger than " + MAX_ARCHIVE_BYTES + " bytes");
    }
    long decompressed = 0;
    int entries = 0;
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
      ZipEntry entry;
      byte[] buffer = new byte[8192];
      while ((entry = zip.getNextEntry()) != null) {
        if (++entries > MAX_ENTRIES) {
          return Optional.of("Archive has more than " + MAX_ENTRIES + " entries");
        }
        String name = entry.getName().replace('\\', '/');
        if (name.startsWith("/") || name.contains("../") || name.equals("..")) {
          return Optional.of("Archive entry outside its folder: " + entry.getName());
        }
        int read;
        while ((read = zip.read(buffer)) > 0) {
          decompressed += read;
          if (decompressed > MAX_DECOMPRESSED_BYTES) {
            return Optional.of("Archive decompresses to more than " + MAX_DECOMPRESSED_BYTES);
          }
        }
      }
    } catch (IOException e) {
      return Optional.of("Unreadable archive");
    }
    return entries == 0 ? Optional.of("Empty archive") : Optional.empty();
  }
}
