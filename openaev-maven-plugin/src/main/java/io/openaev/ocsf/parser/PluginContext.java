package io.openaev.ocsf.parser;

import java.nio.file.Path;
import lombok.Getter;

@Getter
public class PluginContext {
  private final Path pluginResourcesDirectory;
  private final Path rootOpenAEVAPISourceDirectory;

  public PluginContext(Path pluginResourcesDirectory, Path rootOpenAEVAPISourceDirectory) {
    this.pluginResourcesDirectory = pluginResourcesDirectory;
    this.rootOpenAEVAPISourceDirectory = rootOpenAEVAPISourceDirectory;
  }
}
