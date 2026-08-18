package io.openaev.ocsf.parser.schema.source;

import io.openaev.ocsf.parser.PluginContext;
import io.openaev.ocsf.parser.schema.SchemaDimension;
import io.openaev.ocsf.parser.schema.Version;
import io.openaev.ocsf.parser.schema.source.files.ReferentialResource;
import io.openaev.ocsf.parser.schema.source.files.ResourceKey;
import java.io.IOException;
import java.util.List;

public class ReferentialSource extends Source {
  public ReferentialSource(Version version, SchemaDimension dimension, PluginContext ctx)
      throws IOException {
    super(version, dimension, ctx);
  }

  public ReferentialSource(
      Version version, SchemaDimension dimension, PluginContext ctx, String name)
      throws IOException {
    super(version, dimension, ctx, name, null);
  }

  public List<ResourceKey> getSubsourceKeys() throws IOException {
    if (this.fileResource instanceof ReferentialResource) {
      return ((ReferentialResource) this.fileResource).getSubresourceKeys();
    }
    return List.of();
  }
}
