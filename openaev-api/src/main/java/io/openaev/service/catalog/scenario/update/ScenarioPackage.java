package io.openaev.service.catalog.scenario.update;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.rest.exception.BadRequestException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** The scenario export a catalogue release holds. */
public record ScenarioPackage(JsonNode root) {

  // The injects point to them by IDs that only an import maps to the tenant's own.
  private static final List<String> IMPORTED_LINKS =
      List.of(
          "scenario_documents", "scenario_channels", "scenario_articles", "scenario_challenges");

  public static ScenarioPackage read(byte[] archive, ObjectMapper mapper) {
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        if (!entry.isDirectory() && entry.getName().endsWith(".json")) {
          JsonNode root = mapper.readTree(zip.readAllBytes());
          if (root.has("scenario_information")) {
            return new ScenarioPackage(root);
          }
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    throw new BadRequestException("The package holds no scenario");
  }

  public boolean linksImportedContent() {
    return IMPORTED_LINKS.stream().anyMatch(field -> !root.path(field).isEmpty());
  }
}
