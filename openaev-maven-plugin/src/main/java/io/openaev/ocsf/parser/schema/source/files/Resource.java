package io.openaev.ocsf.parser.schema.source.files;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.ocsf.parser.PluginContext;
import io.openaev.ocsf.parser.schema.Version;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public abstract class Resource {
  private final String baseResourcePath = "ocsf/schemas";
  private final Path baseSchemaDirectoryPath;

  protected abstract String getResourceName();

  private Version version;
  private final PluginContext ctx;

  public Resource(Version version, PluginContext ctx) throws IOException {
    this.version = version;
    this.ctx = ctx;
    this.baseSchemaDirectoryPath =
        ctx.getPluginResourcesDirectory()
            .resolve(baseResourcePath)
            .resolve(version.getVersionNumber().getValue());
    ensureDirectoryExists(getFullDirectoryPath());
  }

  protected String getResourceSubPath() {
    return "";
  }

  private Path getFullDirectoryPath() {
    return baseSchemaDirectoryPath.resolve(getResourceSubPath());
  }

  private Path getFullFilepath() {
    return getFullDirectoryPath().resolve(getResourceName() + ".json");
  }

  private void ensureDirectoryExists(Path path) throws IOException {
    if (Files.exists(path)) return;
    Files.createDirectories(path);
  }

  public void write(JsonNode node) throws IOException {
    try (FileOutputStream fs = new FileOutputStream(getFullFilepath().toString())) {
      fs.write(node.toPrettyString().getBytes(StandardCharsets.UTF_8));
    }
  }

  public JsonNode read() throws IOException {
    try (FileInputStream fis = new FileInputStream(getFullFilepath().toString())) {
      return new ObjectMapper().readTree(fis.readAllBytes());
    }
  }
}
