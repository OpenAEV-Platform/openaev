package io.openaev.ocsf.parser.schema.source.files;

import io.openaev.ocsf.parser.PluginContext;
import io.openaev.ocsf.parser.schema.Version;
import java.io.IOException;

public class SingleClassResource extends Resource {
  private final String name;
  private final String extension;

  public SingleClassResource(Version version, PluginContext ctx, String name, String extension)
      throws IOException {
    super(version, ctx);
    this.name = name;
    this.extension = extension;
  }

  @Override
  protected String getResourceName() {
    return this.name;
  }

  @Override
  protected String getResourceSubPath() {
    StringBuilder sb = new StringBuilder("classes");
    if (extension != null && !extension.isBlank()) {
      sb.append("/").append(extension);
    }
    return sb.toString();
  }
}
