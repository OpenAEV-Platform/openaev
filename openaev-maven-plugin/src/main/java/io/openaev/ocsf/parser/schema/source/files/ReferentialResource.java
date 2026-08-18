package io.openaev.ocsf.parser.schema.source.files;

import io.openaev.ocsf.parser.PluginContext;
import io.openaev.ocsf.parser.schema.Version;
import java.io.IOException;
import java.util.List;

public abstract class ReferentialResource extends Resource {
  public ReferentialResource(Version version, PluginContext ctx) throws IOException {
    super(version, ctx);
  }

  public abstract List<ResourceKey> getSubresourceKeys() throws IOException;
}
