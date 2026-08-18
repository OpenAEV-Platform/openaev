package io.openaev.ocsf.parser.schema.source.files;

import io.openaev.ocsf.parser.PluginContext;
import io.openaev.ocsf.parser.schema.Version;
import java.io.IOException;
import java.util.List;

public class ClassesResource extends ReferentialResource {
  public ClassesResource(Version version, PluginContext ctx) throws IOException {
    super(version, ctx);
  }

  @Override
  public List<ResourceKey> getSubresourceKeys() {
    return List.of();
  }

  @Override
  protected String getResourceName() {
    return "classes";
  }
}
