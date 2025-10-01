package io.openaev.utilstest;

import static java.util.stream.Collectors.toMap;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ZipUtils {

  private ZipUtils() {}

  public static Map<String, byte[]> extractAllFilesFromZip(byte[] zipBytes) throws Exception {
    Map<String, byte[]> files = new LinkedHashMap<>();
    try (ZipInputStream zis =
        new ZipInputStream(new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        if (!entry.isDirectory()) {
          ByteArrayOutputStream bos = new ByteArrayOutputStream();
          zis.transferTo(bos);
          files.put(entry.getName(), bos.toByteArray());
        }
      }
    }
    return files;
  }

  public static Map<String, String> convertToJson(Map<String, byte[]> files) {
    return files.entrySet().stream()
        .filter(entry -> entry.getKey().toLowerCase().endsWith(".json"))
        .collect(
            toMap(
                Map.Entry::getKey, entry -> new String(entry.getValue(), StandardCharsets.UTF_8)));
  }
}
