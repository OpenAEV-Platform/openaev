package io.openaev.ocsf.parser.schema.source.files;

import io.openaev.ocsf.parser.PluginContext;
import io.openaev.ocsf.parser.schema.Version;
import java.io.IOException;

public class ObjectsResource extends Resource {
  public ObjectsResource(Version version, PluginContext ctx) throws IOException {
    super(version, ctx);
  }

  @Override
  protected String getResourceName() {
    return "objects";
  }
}
