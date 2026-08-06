package io.openaev.ocsf.parser;

import lombok.Getter;

import java.nio.file.Path;

@Getter
public class PluginContext {
    private final Path pluginResourcesDirectory;
    private final Path rootOpenAEVAPISourceDirectory;

    public PluginContext(Path pluginResourcesDirectory, Path rootOpenAEVAPISourceDirectory) {
        this.pluginResourcesDirectory = pluginResourcesDirectory;
        this.rootOpenAEVAPISourceDirectory = rootOpenAEVAPISourceDirectory;
    }
}
