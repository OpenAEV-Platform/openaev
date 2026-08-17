package io.openaev.ocsf.parser.schema.source.files;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.ocsf.parser.PluginContext;
import io.openaev.ocsf.parser.schema.Version;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class DatatypesResource extends ReferentialResource {
  public DatatypesResource(Version version, PluginContext ctx) throws IOException {
    super(version, ctx);
  }

  @Override
  public List<String> getSubresourceKeys() throws IOException {
    JsonNode contents = read();

    return contents.propertyStream().map(Map.Entry::getKey).toList();
  }

  @Override
  protected String getResourceName() {
    return "data-types";
  }
}
