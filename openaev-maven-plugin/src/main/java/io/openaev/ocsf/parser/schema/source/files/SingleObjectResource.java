package io.openaev.ocsf.parser.schema.source.files;

import io.openaev.ocsf.parser.PluginContext;
import io.openaev.ocsf.parser.client.url.OcsfSchemaExtension;
import io.openaev.ocsf.parser.schema.Version;
import java.io.IOException;

public class SingleObjectResource extends Resource {
  private final String name;
  private final OcsfSchemaExtension extension;

  public SingleObjectResource(
      Version version, PluginContext ctx, String name, OcsfSchemaExtension extension)
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
    StringBuilder sb = new StringBuilder("objects");
    if (extension != null) {
      sb.append("/").append(extension.getValue());
    }
    return sb.toString();
  }
}
