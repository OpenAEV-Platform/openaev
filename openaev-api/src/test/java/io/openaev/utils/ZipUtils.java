package io.openaev.utils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ZipUtils {
  public static <T> T getZipEntry(
      byte[] zipBlob, String entryName, Function<InputStream, T> readFunc) throws IOException {
    try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBlob))) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        if (entry.getName().equals(entryName)) {
          return readFunc.apply(zis);
        }
      }
      // no zip entry corresponding to expected name
      throw new IOException("Zip entry '%s' not found".formatted(entryName));
    }
  }

  public static String streamToString(InputStream is) {
    try {
      return new String(is.readAllBytes());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static byte[] streamToBytes(InputStream is) {
    try {
      return is.readAllBytes();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
