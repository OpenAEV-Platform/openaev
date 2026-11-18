package io.openaev.utils.fixtures;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.service.catalog.CatalogArchiveChecks;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Manifest entries and files of a catalogue release, as the collector sends them. */
public class CatalogReleaseFixture {

  public static final String REPOSITORY = "filigran/scenarios";
  public static final String SCENARIO_ID = "easm-scenario";
  public static final String PAYLOAD_ID = "0f0c4a51-6b1e-4f3c-9d1a-2f3c4b5d6e7f";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private CatalogReleaseFixture() {}

  // -- FILES --

  public static byte[] archive(String entryName, byte[] content) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(out)) {
      zip.putNextEntry(new ZipEntry(entryName));
      zip.write(content);
      zip.closeEntry();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return out.toByteArray();
  }

  /** A scenario archive; a different content gives a different hash. */
  public static byte[] archive(String content) {
    return archive("scenario.json", content.getBytes(StandardCharsets.UTF_8));
  }

  /** The JSON file of a payload version, holding the ID it is listed under. */
  public static byte[] payloadFile(String externalId) {
    try {
      return MAPPER.writeValueAsBytes(
          Map.of(
              "payload_external_id",
              externalId,
              "payload_name",
              "Nmap scan",
              "payload_platforms",
              List.of("Linux"),
              "payload_domains",
              List.of(Map.of("domain_name", "Network"))));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  public static String scenarioFileName(String version) {
    return SCENARIO_ID + "-" + version + ".zip";
  }

  public static String payloadFileName(String version) {
    return "nmap-" + version + ".json";
  }

  // -- MANIFEST --

  public static Map<String, Object> fileEntry(String name, byte[] content) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("name", name);
    entry.put("sha256", CatalogArchiveChecks.sha256(content));
    entry.put("size", (long) content.length);
    return entry;
  }

  public static Map<String, Object> scenarioItem(String version, String status, byte[] archive) {
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("type", "time-based");
    item.put("external_id", SCENARIO_ID);
    item.put("version", version);
    item.put("status", status);
    item.put("name", "EASM Scenario");
    item.put("archive", fileEntry(scenarioFileName(version), archive));
    return item;
  }

  public static Map<String, Object> payloadVersion(String version, byte[] file) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("payload", "nmap-scan");
    payload.put("version", version);
    payload.put("sha256", "a".repeat(64));
    payload.put("openaev_id", PAYLOAD_ID);
    payload.put("file", fileEntry(payloadFileName(version), file));
    return payload;
  }

  public static Map<String, Object> manifest(
      String release, List<Map<String, Object>> items, List<Map<String, Object>> payloads) {
    Map<String, Object> manifest = new LinkedHashMap<>();
    manifest.put("schema_version", 1);
    manifest.put("repository", REPOSITORY);
    manifest.put("release", release);
    manifest.put("items", items);
    manifest.put("payloads", payloads);
    return manifest;
  }
}
