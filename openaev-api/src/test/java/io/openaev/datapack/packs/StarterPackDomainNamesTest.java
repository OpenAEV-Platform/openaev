package io.openaev.datapack.packs;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.rest.domain.enums.PresetDomain;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every domain a shipped starter pack carries must be a preset: the importer creates any other name
 * as a new domain in each tenant it is imported into.
 */
@DisplayName("Starter pack domain names")
class StarterPackDomainNamesTest {

  private static final Path STARTER_PACK_DIR = Path.of("src/main/resources/starterpack");
  private static final ObjectMapper MAPPER = new ObjectMapper();

  static Stream<Path> starterPackArchives() throws IOException {
    try (Stream<Path> files = Files.walk(STARTER_PACK_DIR)) {
      return files.filter(file -> file.toString().endsWith(".zip")).sorted().toList().stream();
    }
  }

  private static List<String> domainNamesOf(Path archive) throws IOException {
    List<String> names = new ArrayList<>();
    try (ZipFile zip = new ZipFile(archive.toFile())) {
      for (ZipEntry entry : Collections.list(zip.entries())) {
        if (!entry.getName().endsWith(".json")) {
          continue;
        }
        try (InputStream in = zip.getInputStream(entry)) {
          collectDomainNames(MAPPER.readTree(in), names);
        }
      }
    }
    return names;
  }

  /** Walks the tree, including JSON documents serialized into string fields. */
  private static void collectDomainNames(JsonNode node, List<String> names) {
    if (node.isObject()) {
      for (Map.Entry<String, JsonNode> field : node.properties()) {
        if ("domain_name".equals(field.getKey()) && field.getValue().isTextual()) {
          names.add(field.getValue().textValue());
        } else {
          collectDomainNames(field.getValue(), names);
        }
      }
    } else if (node.isArray()) {
      node.forEach(element -> collectDomainNames(element, names));
    } else if (node.isTextual()) {
      String text = node.textValue().strip();
      if (text.startsWith("{") || text.startsWith("[")) {
        try {
          collectDomainNames(MAPPER.readTree(text), names);
        } catch (JsonProcessingException notJson) {
          // Free text that merely starts with a brace carries no domain.
        }
      }
    }
  }

  @Test
  @DisplayName("given_shippedStarterPacks_should_carryAtLeastOneDomain")
  void given_shippedStarterPacks_should_carryAtLeastOneDomain() throws IOException {
    // Arrange
    List<Path> archives = starterPackArchives().toList();

    // Act
    List<String> names = new ArrayList<>();
    for (Path archive : archives) {
      names.addAll(domainNamesOf(archive));
    }

    // Assert
    assertThat(archives).isNotEmpty();
    assertThat(names).contains(PresetDomain.getTabletop().getName());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("starterPackArchives")
  @DisplayName("given_starterPack_should_onlyNamePresetDomains")
  void given_starterPack_should_onlyNamePresetDomains(Path archive) throws IOException {
    // Act
    List<String> names = domainNamesOf(archive);

    // Assert
    assertThat(names)
        .as("domain names carried by %s", archive.getFileName())
        .allSatisfy(name -> assertThat(PresetDomain.getNames()).contains(name));
  }
}
