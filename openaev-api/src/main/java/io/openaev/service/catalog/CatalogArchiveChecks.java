package io.openaev.service.catalog;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Consumer checks of RFC 0004 §4.6 run before a catalogue archive is used: integrity and size. */
public final class CatalogArchiveChecks {

  // RFC 0004 question 1 positions: archive 20 MiB, decompressed 100 MiB, 1,000 entries.
  static final long MAX_ARCHIVE_BYTES = 20L * 1024 * 1024;
  static final long MAX_DECOMPRESSED_BYTES = 100L * 1024 * 1024;
  static final int MAX_ENTRIES = 1000;
  // A zip bomb decompresses far beyond what JSON and documents do; small entries are left alone.
  static final long MAX_COMPRESSION_RATIO = 100;
  static final long RATIO_CHECK_FROM_BYTES = 1024L * 1024;

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
    Optional<Directory> directory = directory(archive);
    if (directory.isEmpty()) {
      return Optional.of("Unreadable archive");
    }
    if (directory.get().symbolicLink()) {
      return Optional.of("Archive holds a symbolic link");
    }
    long decompressed = 0;
    int entries = 0;
    List<String> streamed = new ArrayList<>();
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
      ZipEntry entry;
      byte[] buffer = new byte[8192];
      while ((entry = zip.getNextEntry()) != null) {
        if (++entries > MAX_ENTRIES) {
          return Optional.of("Archive has more than " + MAX_ENTRIES + " entries");
        }
        streamed.add(entry.getName());
        String name = entry.getName().replace('\\', '/');
        if (name.startsWith("/") || name.contains("../") || name.equals("..")) {
          return Optional.of("Archive entry outside its folder: " + entry.getName());
        }
        int read;
        long entryBytes = 0;
        while ((read = zip.read(buffer)) > 0) {
          decompressed += read;
          entryBytes += read;
          if (decompressed > MAX_DECOMPRESSED_BYTES) {
            return Optional.of("Archive decompresses to more than " + MAX_DECOMPRESSED_BYTES);
          }
        }
        long compressed = entry.getCompressedSize();
        if (entryBytes > RATIO_CHECK_FROM_BYTES
            && compressed > 0
            && entryBytes / compressed > MAX_COMPRESSION_RATIO) {
          return Optional.of("Archive entry compressed too much: " + entry.getName());
        }
      }
    } catch (IOException e) {
      return Optional.of("Unreadable archive");
    }
    if (entries == 0) {
      return Optional.of("Empty archive");
    }
    // The install reads the directory: an entry the checks above did not see must not get there.
    List<String> listed = new ArrayList<>(directory.get().names());
    Collections.sort(listed);
    Collections.sort(streamed);
    return listed.equals(streamed)
        ? Optional.empty()
        : Optional.of("Archive entries do not match its directory");
  }

  record Directory(List<String> names, boolean symbolicLink) {}

  /**
   * The entries of the central directory, and whether one carries the Unix symbolic link mode,
   * which {@code java.util.zip} does not expose; empty when the directory cannot be read.
   */
  static Optional<Directory> directory(byte[] archive) {
    ByteBuffer zip = ByteBuffer.wrap(archive).order(ByteOrder.LITTLE_ENDIAN);
    int end = -1;
    for (int i = archive.length - 22; i >= Math.max(0, archive.length - 22 - 65535); i--) {
      if (zip.getInt(i) == 0x06054b50) {
        end = i;
        break;
      }
    }
    if (end < 0) {
      return Optional.empty();
    }
    List<String> names = new ArrayList<>();
    boolean symbolicLink = false;
    int count = Short.toUnsignedInt(zip.getShort(end + 10));
    long offset = Integer.toUnsignedLong(zip.getInt(end + 16));
    for (int n = 0; n < count; n++) {
      if (offset + 46 > archive.length || zip.getInt((int) offset) != 0x02014b50) {
        return Optional.empty();
      }
      int pos = (int) offset;
      int unixMode = (int) (Integer.toUnsignedLong(zip.getInt(pos + 38)) >>> 16);
      symbolicLink |= (unixMode & 0xF000) == 0xA000;
      int nameLength = Short.toUnsignedInt(zip.getShort(pos + 28));
      if (pos + 46 + nameLength > archive.length) {
        return Optional.empty();
      }
      names.add(new String(archive, pos + 46, nameLength, StandardCharsets.UTF_8));
      offset +=
          46
              + nameLength
              + Short.toUnsignedInt(zip.getShort(pos + 30))
              + Short.toUnsignedInt(zip.getShort(pos + 32));
    }
    return Optional.of(new Directory(names, symbolicLink));
  }
}
