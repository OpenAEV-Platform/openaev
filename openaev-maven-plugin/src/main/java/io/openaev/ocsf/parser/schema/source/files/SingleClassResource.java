package io.openaev.ocsf.parser.schema.source.files;

import io.openaev.ocsf.parser.PluginContext;
import io.openaev.ocsf.parser.schema.Version;
import java.io.IOException;

public class SingleClassResource extends Resource {
  private final String name;

  public SingleClassResource(Version version, PluginContext ctx, String name) throws IOException {
    super(version, ctx);
    this.name = name;
  }

  @Override
  protected String getResourceName() {
    return this.name;
  }

  @Override
  protected String getResourceSubPath() {
    return "classes";
  }
}
